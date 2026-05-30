// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.InvocationApi
import dev.restate.admin.api.ServiceApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.ModifyServiceStateRequest
import dev.restate.client.Client
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toService
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdktesting.infra.ContainerServiceDeploymentConfig
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectContainerPort
import dev.restate.sdktesting.infra.InjectReportDir
import dev.restate.sdktesting.infra.RESTATE_RUNTIME
import dev.restate.sdktesting.infra.RUNTIME_NODE_PORT
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.infra.ServiceSpec
import dev.restate.sdktesting.invokermemory.MemoryPressureService
import dev.restate.sdktesting.invokermemory.StatefulObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.*
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
class InvokerMemoryTest {

  companion object {
    private val LOG = LogManager.getLogger(InvokerMemoryTest::class.java)

    /**
     * Selects how the InvokerMemoryTest services are deployed:
     * * `kotlin` (default) — in-process Kotlin services via `Endpoint.bind(...)`; SDK shares the
     *   test JVM and the JVM-side diagnostics (heap log, watchdog heap field, thread dump on
     *   timeout) target it.
     * * `kotlin-container` — the same Kotlin services packaged into a separate JVM Docker image
     *   (`DEFAULT_KOTLIN_IMAGE`). Isolates "in-process vs out-of-process" for the Kotlin SDK.
     * * `ts` — TypeScript service Docker image (`DEFAULT_TS_IMAGE`).
     */
    private val SDK_MODE = System.getenv("INVOKER_MEMORY_TEST_SDK") ?: "kotlin"

    private val USE_TS_SERVICE = SDK_MODE == "ts"
    private val USE_KOTLIN_CONTAINER_SERVICE = SDK_MODE == "kotlin-container"
    private val USE_CONTAINER_SERVICE = USE_TS_SERVICE || USE_KOTLIN_CONTAINER_SERVICE

    private const val DEFAULT_TS_IMAGE = "ghcr.io/restatedev/e2e-invoker-memory-ts:0.1.0"
    private const val DEFAULT_KOTLIN_IMAGE = "ghcr.io/restatedev/e2e-invoker-memory-kotlin:0.1.0"

    /** Dump all JVM thread stacks (including the in-process SDK's Vert.x event loops) to a file. */
    private fun dumpAllThreads(path: Path) {
      val sb = StringBuilder()
      Thread.getAllStackTraces().forEach { (thread, frames) ->
        sb.append("\"${thread.name}\" state=${thread.state}\n")
        frames.forEach { sb.append("\tat $it\n") }
        sb.append("\n")
      }
      Files.writeString(path, sb.toString())
    }

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
      withEnv("RESTATE_WORKER__INVOKER__PER_INVOCATION_MEMORY_LIMIT", "96KiB")
      // Enable the experimental invoker yield feature
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_INVOKER_YIELD", "true")
      // Explicit retry policy: fast retries, pause on max attempts.
      // Required for the oversized-payload test to reach "paused" status.
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__INITIAL_INTERVAL", "100ms")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__MAX_INTERVAL", "100ms")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__MAX_ATTEMPTS", "10")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__ON_MAX_ATTEMPTS", "pause")

      // Connection-pool repro diagnostics: turn the runtime->SDK keep-alive PING into a 2s liveness
      // heartbeat with a large timeout, so a stall is probed without tearing the connection down.
      // NOTE: env key path is UNVERIFIED against the runtime config schema; confirm before relying.
      withEnv("RESTATE_HTTP_KEEP_ALIVE_OPTIONS__INTERVAL", "2s")
      withEnv("RESTATE_HTTP_KEEP_ALIVE_OPTIONS__TIMEOUT", "240s")

      // Disable journal retention so that completed journal entries are cleaned up immediately.
      // This test creates large payloads in journal steps and queries sys_journal at the end,
      // so retaining them would bloat the table and slow down the test.
      //
      // The TS path mirrors this via `options: { journalRetention: { seconds: 0 } }` in the
      // service image (see e2e-tests/services/invoker-memory-ts/src/index.ts). The Kotlin
      // container path reuses the same Kotlin classes — same journalRetention applied in
      // e2e-tests/services/invoker-memory-kotlin/src/main/kotlin/.../Main.kt.
      if (USE_CONTAINER_SERVICE) {
        val image =
            if (USE_TS_SERVICE) System.getenv("INVOKER_MEMORY_TEST_TS_IMAGE") ?: DEFAULT_TS_IMAGE
            else System.getenv("INVOKER_MEMORY_TEST_KOTLIN_IMAGE") ?: DEFAULT_KOTLIN_IMAGE
        withServiceDeploymentConfig(
            ServiceSpec.DEFAULT_SERVICE_NAME, ContainerServiceDeploymentConfig(image, emptyMap()))
        withServiceSpec(
            ServiceSpec.defaultBuilder().withServices("MemoryPressureService", "StatefulObject"))
      } else {
        withEndpoint(
            Endpoint.bind(MemoryPressureService()) { it.journalRetention = 0.seconds }
                .bind(StatefulObject()) { it.journalRetention = 0.seconds })
      }
    }
  }

  // Tests in this class share a single deployment (companion-scoped @RegisterExtension), so an
  // invocation left over from a failed/timed-out test would otherwise hold memory leases across
  // the global pool and cascade into the next test. Kill everything still in flight and wait for
  // the invoker memory pool to drain before yielding control to the next @Test.
  private suspend fun drainAndKill(adminURI: URI, metricsPort: Int) {
    val nonTerminalFilter = "status != 'completed'"
    val invocationApi = InvocationApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))

    LOG.info("Killing all invocations")

    // Each poll iteration re-lists pending invocations and re-issues kill for everything
    // still alive, then asserts the list is empty. Awaitility's ignoreExceptions() (set in
    // the untilAsserted wrapper in utils.kt) absorbs transient failures of either the list
    // query or individual kill calls (e.g. 503s during leadership changes, or 404s for
    // invocations that finished between query and kill), so the loop converges on its own.
    await withAlias
        "all invocations killed and terminated" untilAsserted
        {
          val pending = getAllInvocations(adminURI, nonTerminalFilter)
          pending.forEach { invocationApi.killInvocation(it.id) }
          assertThat(pending).isEmpty()
        }

    LOG.info("Awaiting until the invoker memory pool is drained")

    await withAlias
        "invoker memory pool drains" untilAsserted
        {
          assertThat(getInvokerMemoryPoolUsage(metricsPort)).isEqualTo(0.0)
        }
  }

  @AfterEach
  fun cleanupInvocations(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = RESTATE_RUNTIME, port = RUNTIME_NODE_PORT) metricsPort: Int,
  ) = runBlocking { drainAndKill(adminURI, metricsPort) }

  @Timeout(120)
  @Test
  @DisplayName("All invocations complete under memory pressure with invoker yield")
  fun allInvocationsCompleteUnderMemoryPressure(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = RESTATE_RUNTIME, port = RUNTIME_NODE_PORT) metricsPort: Int,
      @InjectReportDir reportDir: Path,
  ) =
      runTest(timeout = 2.minutes) {
        val client = ingressClient.toService<MemoryPressureService>()
        val count = 50

        // Log the app JVM's resolved limits (no -Xmx is set, so max heap is the JDK ergonomic
        // default ~25% of runner RAM). Helps correlate stalls with the actual heap/CPU budget.
        // Only relevant when the SDK runs in-process; container modes run the SDK in a separate
        // process (Node for "ts", a separate JVM for "kotlin-container").
        if (!USE_CONTAINER_SERVICE) {
          Runtime.getRuntime().let {
            LOG.info(
                "JVM env: maxHeapMiB={}, totalHeapMiB={}, availableProcessors={}",
                it.maxMemory() / (1024 * 1024),
                it.totalMemory() / (1024 * 1024),
                it.availableProcessors())
          }
        }

        // Run the invocations, the progress watchdog and the bounded await on a REAL dispatcher.
        // runTest's virtual clock would otherwise fast-forward the 90s timeout the instant the test
        // dispatcher idles (while the network calls are suspended), cancelling the in-flight calls.
        val results =
            withContext(Dispatchers.IO) {
              withTimeoutOrNull(90.seconds) {
                // 50 x 64KB = 3.2MB of inbound memory; pool is only 1MB, so some invocations must
                // yield and resume for the others to make progress. Created inside the timeout so
                // a real timeout structurally cancels the in-flight calls (no hang on teardown).
                val deferreds =
                    (1..count).map { i ->
                      async {
                        client
                            .request { generate("key-$i") }
                            .options(idempotentCallOptions)
                            .call()
                            .response
                      }
                    }

                // Progress watchdog: every 2s log completion count and invoker pool usage so a
                // progressive slowdown is visible in testrunner.log before a stall. The
                // in-process Kotlin path also logs JVM heap usage (this JVM hosts the SDK);
                // container modes drop it because the SDK runs in a separate process whose
                // heap is invisible from here.
                val watchdog = launch {
                  while (isActive) {
                    delay(2.seconds)
                    val done = deferreds.count { it.isCompleted }
                    val pool =
                        runCatching { getInvokerMemoryPoolUsage(metricsPort) }.getOrDefault(-1.0)
                    if (USE_CONTAINER_SERVICE) {
                      LOG.info("watchdog: {}/{} completed, invokerPoolBytes={}", done, count, pool)
                    } else {
                      val rt = Runtime.getRuntime()
                      val heapMiB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                      LOG.info(
                          "watchdog: {}/{} completed, invokerPoolBytes={}, heapUsedMiB={}",
                          done,
                          count,
                          pool,
                          heapMiB)
                    }
                  }
                }

                try {
                  deferreds.awaitAll()
                } finally {
                  watchdog.cancel()
                }
              }
            }

        // On timeout the bounded await returns null (and structurally cancels the in-flight calls).
        // For the Kotlin in-process path, capture a thread dump before the 120s @Timeout kills the
        // JVM — the decisive "stuck on what?" probe of the in-process Vert.x event-loop threads.
        // Container modes run the SDK in a separate process, so a thread dump of this JVM tells
        // us nothing about the SDK.
        if (results == null) {
          if (USE_CONTAINER_SERVICE) {
            throw AssertionError("Timed out after 90s waiting for $count invocations")
          } else {
            val dump =
                reportDir.resolve("InvokerMemoryTest-threaddump-${System.currentTimeMillis()}.txt")
            dumpAllThreads(dump)
            throw AssertionError(
                "Timed out after 90s waiting for $count invocations; thread dump at $dump")
          }
        }

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

  @Disabled
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

  @Disabled
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

  @Disabled
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
