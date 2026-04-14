// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.client.Client
import dev.restate.client.kotlin.resolveSuspend
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdktesting.infra.*
import dev.restate.serde.kotlinx.jsonSerde
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Regression test for awakeable signal loss during leadership transitions.
 *
 * When a partition leadership changes, the old leader's self-proposed commands can be committed to
 * the Bifrost log AFTER the new leader's AnnounceLeader entry. The dedup mechanism then drops these
 * commands. The awakeable resolve path (append_signal) should not be affected since signals don't
 * use dedup information.
 *
 * This test creates awakeables and resolves them via the ingress API during leadership transfers.
 * Each accepted resolve should lead to the corresponding invocation completing.
 */
class AwakeableLeaderTransferTest {

  @VirtualObject
  class AwakeableHolder {
    @Handler
    suspend fun run(): String {
      val awk = awakeable<String>()
      state().set("awk_id", awk.id)
      return awk.await()
    }

    @Shared suspend fun getAwakeableId(): String = state().get<String>("awk_id") ?: ""
  }

  companion object {
    private val LOG = LogManager.getLogger(AwakeableLeaderTransferTest::class.java)

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(AwakeableHolder()))
      withEnv("RESTATE_DEFAULT_NUM_PARTITIONS", "1")
    }
  }

  private data class AwakeableInfo(
      val key: String,
      val idempotencyKey: String,
      val awakeableId: String,
  )

  @Test
  @Timeout(180)
  @Tag("only-multi-node")
  fun awakeableCompletionsAreNotLostDuringLeaderTransfer(
      @InjectClient ingressClient: Client,
      @InjectContainerHandle(hostName = RESTATE_RUNTIME) runtimeHandle: ContainerHandle,
  ) =
      runTest(timeout = 180.seconds) {
        val numNodes = getGlobalConfig().restateNodes
        val container = runtimeHandle.container

        // Reconfigure partition replication to match the number of nodes so that
        // every node runs a partition processor (leader or follower) for partition 0.
        val configResult =
            withContext(Dispatchers.IO) {
              container.execInContainer(
                  "restatectl",
                  "config",
                  "set",
                  "--yes",
                  "--partition-replication",
                  numNodes.toString())
            }
        assertThat(configResult.exitCode)
            .describedAs(
                "restatectl config set failed (exit=${configResult.exitCode}): ${configResult.stderr}")
            .isZero()
        LOG.info("Set partition replication to {}", numNodes)

        val numAwakeables = 30

        // Phase 1: Create invocations waiting on awakeables and collect their IDs.
        LOG.info("Phase 1: Creating $numAwakeables invocations and collecting awakeable IDs")

        val awakeables =
            (1..numAwakeables)
                .map { i ->
                  async {
                    val key = "awk-$i"
                    val idempotencyKey = "run-$key"
                    val client = ingressClient.toVirtualObject<AwakeableHolder>(key)

                    // Start the handler (fire-and-forget)
                    client.request { run() }.options { this.idempotencyKey = idempotencyKey }.send()

                    // Poll until the awakeable ID is stored in state
                    await withAlias
                        "awakeable $key is registered" untilAsserted
                        {
                          assertThat(client.request { getAwakeableId() }.call().response)
                              .isNotBlank()
                        }

                    val awkId = client.request { getAwakeableId() }.call().response
                    AwakeableInfo(key, idempotencyKey, awkId)
                  }
                }
                .awaitAll()

        LOG.info("Collected ${awakeables.size}/$numAwakeables awakeable IDs")
        assertThat(awakeables).hasSize(numAwakeables)

        // Phase 2: Start leadership transfers in the background, then resolve awakeables.
        // The leadership pin cycles through all nodes so the partition processor leader
        // keeps changing while we resolve awakeables through the ingress.
        LOG.info(
            "Phase 2: Starting leadership transfers and resolving ${awakeables.size} awakeables")

        val transferJob =
            launch(Dispatchers.IO) {
              var nodeIndex = 0
              while (true) {
                ensureActive()
                val targetNode = (nodeIndex % numNodes) + 1
                nodeIndex++
                try {
                  val result =
                      container.execInContainer(
                          "restatectl",
                          "partition",
                          "leader",
                          "pin",
                          "0",
                          "--node",
                          targetNode.toString())
                  if (result.exitCode == 0) {
                    LOG.debug("Leadership pinned to node N{}", targetNode)
                  } else {
                    LOG.warn(
                        "Leadership pin to N{} failed (exit={}): {}",
                        targetNode,
                        result.exitCode,
                        result.stderr)
                  }
                } catch (e: Exception) {
                  LOG.warn("restatectl exec failed: {}", e.message)
                }
              }
            }

        // Resolve awakeables while leadership transfers are happening.
        var acceptedResolves = 0
        val resolvedAwakeables = mutableListOf<AwakeableInfo>()

        for (awk in awakeables) {
          try {
            ingressClient
                .awakeableHandle(awk.awakeableId)
                .resolveSuspend(jsonSerde(), "resolved-${awk.key}")
            acceptedResolves++
            resolvedAwakeables.add(awk)
            LOG.debug("Resolved awakeable for {}", awk.key)
          } catch (e: Exception) {
            LOG.warn("Failed to resolve awakeable for {}: {}", awk.key, e.message)
          }
          // Real-time pause to spread resolves across leadership transitions.
          // TODO(slinkydeveloper): Remove Dispatchers.IO workaround once runTest virtual-time is
          // replaced.
          withContext(Dispatchers.IO) { delay(100) }
        }

        LOG.info("{}/{} resolves accepted", acceptedResolves, awakeables.size)
        assertThat(acceptedResolves)
            .describedAs("at least one resolve should be accepted")
            .isGreaterThan(0)

        // Phase 3: Stop leadership transfers and verify every accepted resolve
        // led to a completed invocation.
        transferJob.cancelAndJoin()

        LOG.info(
            "Phase 3: Verifying {} accepted resolves led to completed invocations",
            acceptedResolves)

        var completed = 0
        for (awk in resolvedAwakeables) {
          val client = ingressClient.toVirtualObject<AwakeableHolder>(awk.key)
          try {
            // Re-send with the same idempotency key — this attaches to the original
            // invocation and returns its result once it completes.
            await withAlias
                "invocation ${awk.key} completes" withTimeout
                60.seconds untilAsserted
                {
                  val result =
                      client
                          .request { run() }
                          .options { idempotencyKey = awk.idempotencyKey }
                          .call()
                          .response
                  assertThat(result).isEqualTo("resolved-${awk.key}")
                }
            completed++
          } catch (e: Exception) {
            LOG.warn("Invocation for {} did not complete: {}", awk.key, e.message)
          }
        }

        assertThat(completed)
            .withFailMessage(
                "${acceptedResolves - completed} out of $acceptedResolves accepted awakeable resolves " +
                    "did not complete. Signals were lost during leadership transitions.")
            .isEqualTo(acceptedResolves)

        LOG.info(
            "All {} accepted awakeable resolves led to completed invocations", acceptedResolves)
      }
}
