// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.ServiceApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.ModifyServiceStateRequest
import dev.restate.client.Client
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toService
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectContainerPort
import dev.restate.sdktesting.infra.RESTATE_RUNTIME
import dev.restate.sdktesting.infra.RUNTIME_NODE_PORT
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests that the invoker memory management works correctly under pressure.
 *
 * Four scenarios are tested:
 * 1. Side-effect pressure: 50 concurrent invocations each generating 10 × 64 KiB of side-effect
 *    output, forcing yields when invocations exhausts the memory pool.
 * 2. State loading pressure: 50 virtual objects each with 64 KiB of state (2 × 32 KiB entries)
 *    invoked concurrently, forcing yields when loading state exhausts the memory pool.
 * 3. Oversized payload: A single invocation producing a run output (512 KiB) that exceeds the
 *    per-invocation memory limit (256 KiB), verifying the server pauses rather than looping.
 * 4. Oversized state: A virtual object with 512 KiB of state (injected via admin API) that exceeds
 *    the per-invocation memory limit (256 KiB), verifying the server pauses when it cannot load the
 *    state within the memory budget.
 *
 * The yield tests verify invocations eventually complete correctly. The oversized tests verify the
 * invocation is paused.
 */
@Tag("only-single-node")
class InvokerMemoryTest {

  @Service
  @Name("MemoryPressureService")
  class MemoryPressureService {
    @Handler
    suspend fun generate(input: String): String {
      for (i in 0 until 10) {
        runBlock { randomString(64.kb) }
      }
      return "ok-$input"
    }

    @Handler
    suspend fun generateOversized(input: String): String {
      // Single side effect producing 512KiB — exceeds the 256KiB per-invocation memory limit.
      // The invocation can never make progress and should be paused by the server.
      runBlock { randomString(512.kb) }
      return "ok-$input"
    }
  }

  @VirtualObject
  @Name("StatefulObject")
  class StatefulObject {
    @Handler
    suspend fun initState(input: String) {
      // Store two 32KiB state entries (64KiB total per virtual object)
      state().set("state-a", randomString(32.kb))
      state().set("state-b", randomString(32.kb))
    }

    @Handler
    suspend fun readState(input: String): Int {
      val a = state().get<String>("state-a") ?: ""
      val b = state().get<String>("state-b") ?: ""
      return a.length + b.length
    }

    @Handler
    suspend fun readLargeState(input: String): Int {
      val data = state().get<String>("large-state") ?: ""
      return data.length
    }
  }

  companion object {
    /**
     * Fetch the invoker memory pool usage from the Prometheus metrics endpoint. Returns the value
     * of `restate_memory_pool_usage_bytes{name="invoker"}` as a [Double].
     */
    private fun getInvokerMemoryPoolUsage(metricsPort: Int): Double {
      val request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://127.0.0.1:$metricsPort/metrics"))
              .GET()
              .build()
      val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
      return response
          .body()
          .lineSequence()
          .filter { !it.startsWith("#") }
          .first { "restate_memory_pool_usage_bytes" in it && "name=\"invoker\"" in it }
          .split(" ")
          .last()
          .toDouble()
    }

    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      // Tight memory budget to force yield behavior
      withEnv("RESTATE_WORKER__INVOKER__MEMORY_LIMIT", "1MiB")
      withEnv("RESTATE_WORKER__INVOKER__PER_INVOCATION_INITIAL_MEMORY", "16KiB")
      withEnv("RESTATE_WORKER__INVOKER__PER_INVOCATION_MEMORY_LIMIT", "256KiB")
      // Enable the experimental invoker yield feature
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_INVOKER_YIELD", "true")
      // Explicit retry policy: fast retries, pause on max attempts.
      // Required for the oversized-payload test to reach "paused" status.
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__INITIAL_INTERVAL", "100ms")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__MAX_INTERVAL", "100ms")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__MAX_ATTEMPTS", "10")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__ON_MAX_ATTEMPTS", "pause")

      // Disable journal retention so that completed journal entries are cleaned up immediately.
      // This test creates large payloads in journal steps and queries sys_journal at the end,
      // so retaining them would bloat the table and slow down the test.
      withEndpoint(
          Endpoint.bind(MemoryPressureService()) { it.journalRetention = 0.seconds }
              .bind(StatefulObject()) { it.journalRetention = 0.seconds })
    }
  }

  @Test
  @Timeout(120)
  @DisplayName("All invocations complete under memory pressure with invoker yield")
  fun allInvocationsCompleteUnderMemoryPressure(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = RESTATE_RUNTIME, port = RUNTIME_NODE_PORT) metricsPort: Int,
  ) =
      runTest(timeout = 2.minutes) {
        val client = ingressClient.toService<MemoryPressureService>()
        val count = 50

        // Launch all invocations concurrently. This will require 50 x 64KB = 3.2MB
        // of inbound memory allocation. Since the pool size is only 1MB, some of the
        // invocations must yield and resume for the others to make progress.
        val deferreds =
            (1..count).map { i ->
              async {
                client.request { generate("key-$i") }.options(idempotentCallOptions).call().response
              }
            }

        // Await all results — if any invocation gets stuck, this will time out
        val results = deferreds.awaitAll()

        // Verify all invocations returned correct results
        results.forEachIndexed { index, result ->
          assertThat(result).isEqualTo("ok-key-${index + 1}")
        }

        // Verify all invocations completed via admin query
        val invocations =
            getAllInvocations(
                adminURI,
                "target_service_name = 'MemoryPressureService' AND target_handler_name = 'generate'")
        assertThat(invocations).hasSize(count).allSatisfy { entry ->
          assertThat(entry.status).isEqualTo("completed")
        }

        // Verify all invoker memory has been released.
        // Memory leases are tied to HTTP body frames (via Bytes::from_owner); hyper's
        // connection driver flushes them asynchronously, so we poll instead of asserting
        // immediately.
        await withAlias
            "invoker memory pool returns to zero" untilAsserted
            {
              assertThat(getInvokerMemoryPoolUsage(metricsPort)).isEqualTo(0.0)
            }
      }

  @Test
  @DisplayName("Virtual object invocations yield when state loading exceeds memory budget")
  fun allStatefulInvocationsCompleteUnderMemoryPressure(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = RESTATE_RUNTIME, port = RUNTIME_NODE_PORT) metricsPort: Int,
  ) = runTest {
    val count = 50

    // Phase 1: Initialize state for all 50 virtual objects sequentially.
    // Each gets 64KiB of state (2 x 32KiB entries). Sequential to avoid
    // exceeding the 1MiB memory pool during setup.
    for (i in 1..count) {
      val client = ingressClient.toVirtualObject<StatefulObject>("obj-$i")
      client.request { initState("init") }.options(idempotentCallOptions).call()
    }

    // Phase 2: Invoke all 50 virtual objects in parallel.
    // Each invocation loads ~64KiB of state. 50 * 64KiB = 3.2MiB >> 1MiB pool,
    // so invocations must yield when memory is exhausted and resume when freed.
    val deferreds =
        (1..count).map { i ->
          val client = ingressClient.toVirtualObject<StatefulObject>("obj-$i")
          async {
            client.request { readState("read") }.options(idempotentCallOptions).call().response
          }
        }

    // Await all results — if any invocation gets stuck, this will time out
    val results = deferreds.awaitAll()

    // Verify all invocations returned the correct combined state length (32768 + 32768)
    assertThat(results).hasSize(count).allSatisfy { result -> assertThat(result).isEqualTo(64.kb) }

    // Verify all readState invocations completed via admin query
    val invocations =
        getAllInvocations(
            adminURI,
            "target_service_name = 'StatefulObject' AND target_handler_name = 'readState'")
    assertThat(invocations).hasSize(count).allSatisfy { entry ->
      assertThat(entry.status).isEqualTo("completed")
    }

    // Verify all invoker memory has been released.
    // Memory leases are tied to HTTP body frames (via Bytes::from_owner); hyper's
    // connection driver flushes them asynchronously, so we poll instead of asserting
    // immediately.
    await withAlias
        "invoker memory pool returns to zero" untilAsserted
        {
          assertThat(getInvokerMemoryPoolUsage(metricsPort)).isEqualTo(0.0)
        }
  }

  @Test
  @DisplayName("Invocation is paused when single run output exceeds per-invocation memory limit")
  fun invocationPausedWhenRunExceedsMemoryLimit(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = RESTATE_RUNTIME, port = RUNTIME_NODE_PORT) metricsPort: Int,
  ) = runTest {
    val client = ingressClient.toService<MemoryPressureService>()

    // Send invocation that produces a single run output (512KiB) exceeding the
    // per-invocation memory limit (256KiB). Use send() so we don't block waiting.
    val sendResult =
        client.request { generateOversized("oversized") }.options(idempotentCallOptions).send()
    val invocationId = sendResult.invocationId()

    // Wait until the invocation is paused — the server should detect it can
    // never make progress within the memory limit and pause it.
    await withAlias
        "invocation is paused due to oversized payload" untilAsserted
        {
          val status = getInvocationStatus(adminURI, invocationId)
          assertThat(status.status).isEqualTo("paused")
        }

    // Verify all invoker memory has been released.
    // Memory leases are tied to HTTP body frames (via Bytes::from_owner); hyper's
    // connection driver flushes them asynchronously, so we poll instead of asserting
    // immediately.
    await withAlias
        "invoker memory pool returns to zero" untilAsserted
        {
          assertThat(getInvokerMemoryPoolUsage(metricsPort)).isEqualTo(0.0)
        }
  }

  @Test
  @DisplayName("Invocation is paused when virtual object state exceeds per-invocation memory limit")
  fun invocationPausedWhenStateExceedsMemoryLimit(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = RESTATE_RUNTIME, port = RUNTIME_NODE_PORT) metricsPort: Int,
  ) = runTest {
    val objectKey = "oversized-state-obj"

    // Inject a 512KiB state entry via admin API, bypassing the invoker memory limit.
    // The SDK serializes String state as JSON, so we JSON-encode the value.
    val serviceApi = ServiceApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))
    val largeValue = randomString(512.kb)
    val stateBytes = Json.encodeToString(largeValue).toByteArray().map { it.toInt() }
    val request =
        ModifyServiceStateRequest()
            .objectKey(objectKey)
            .newState(mapOf("large-state" to stateBytes))

    await withAlias
        "inject oversized state via admin API" untilAsserted
        {
          serviceApi.modifyServiceState("StatefulObject", request)
        }

    // Invoke the handler — it needs to load 512KiB of state which exceeds
    // the 256KiB per-invocation memory limit. Use send() so we don't block.
    val client = ingressClient.toVirtualObject<StatefulObject>(objectKey)
    val sendResult = client.request { readLargeState("read") }.options(idempotentCallOptions).send()
    val invocationId = sendResult.invocationId()

    // Wait until the invocation is paused — the server cannot load the state
    // within the per-invocation memory limit and should pause it.
    await withAlias
        "invocation is paused due to oversized state" untilAsserted
        {
          val status = getInvocationStatus(adminURI, invocationId)
          assertThat(status.status).isEqualTo("paused")
        }

    // Verify all invoker memory has been released.
    // Memory leases are tied to HTTP body frames (via Bytes::from_owner); hyper's
    // connection driver flushes them asynchronously, so we poll instead of asserting
    // immediately.
    await withAlias
        "invoker memory pool returns to zero" untilAsserted
        {
          assertThat(getInvokerMemoryPoolUsage(metricsPort)).isEqualTo(0.0)
        }
  }
}
