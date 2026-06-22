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
import dev.restate.client.kotlin.attachSuspend
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.kotlin.awakeableHandle
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.resolve
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdk.kotlin.toVirtualObject
import dev.restate.sdktesting.infra.*
import dev.restate.serde.TypeTag
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies the runtime enforces the rule-book concurrency limit on a scope. Scoped invocations are
 * limited to N in flight, and the held excess progresses as the running ones complete.
 *
 * Runs only on single-node suites: the strict in-process concurrency bound (see
 * [concurrencyLimitIsRespected]) cannot be asserted on multi-node clusters, where a leadership
 * change can momentarily run more than `limit` handlers at once. See
 * https://github.com/restatedev/e2e/issues/435.
 */
class ConcurrencyLimitTest {

  @Service
  @Name("BlockingProxy")
  class BlockingProxy {
    @Handler
    suspend fun block(key: String): String {
      // Track how many block handlers execute concurrently on the SDK endpoint. The scope
      // concurrency limit is enforced on these BlockingProxy invocations, so this directly
      // measures the limit. The counter is incremented on entry and decremented in `finally`, which
      // also runs when the SDK suspends the invocation at the `.await()` below (coroutine
      // cancellation) and on completion. We keep the running maximum so the test can assert the
      // runtime never lets more than `limit` of these handlers run at once - even if leadership
      // changes shuffle *which* invocations hold the slots.
      val current = concurrentBlocks.incrementAndGet()
      maxConcurrentBlocks.accumulateAndGet(current) { a, b -> maxOf(a, b) }
      try {
        return toVirtualObject<Blocker>(key).request { run() }.call().await()
      } finally {
        concurrentBlocks.decrementAndGet()
      }
    }
  }

  @VirtualObject
  @Name("Blocker")
  class Blocker {
    @Handler
    suspend fun run(): String {
      val awk = awakeable<String>()
      state().set("awk", awk.id)
      return awk.await()
    }

    @Shared suspend fun getAwakeable(): String = state().get<String>("awk") ?: ""

    @Shared
    suspend fun resolveAwakeable(value: String) {
      val id = state().get<String>("awk") ?: ""
      if (id.isEmpty()) throw TerminalException("Awakeable not registered yet")
      awakeableHandle(id).resolve(value)
    }
  }

  companion object {
    /** Number of [BlockingProxy.block] handlers currently executing on the in-process endpoint. */
    val concurrentBlocks = AtomicInteger(0)
    /** Running maximum of [concurrentBlocks] observed during a test run. */
    val maxConcurrentBlocks = AtomicInteger(0)

    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(BlockingProxy()).bind(Blocker()))
      // Scoped invocations and rule-book require the vqueues experimental feature.
      // TODO: drop this once the minimum supported Restate version is v1.8, where vqueues is on by
      //   default.
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_VQUEUES", "true")
      // Reduce rule-book activation latency so the test isn't gated on the default 30s poll.
      withEnv("RESTATE_WORKER__RULE_BOOK_POLL_INTERVAL", "1s")
    }
  }

  @Test
  @DisplayName("Concurrency limit on a scope holds excess invocations and releases on completion")
  fun concurrencyLimitIsRespected(
      @InjectIngressURI ingressURI: URI,
      @InjectAdminURI adminURI: URI,
      @InjectContainerHandle(hostName = RESTATE_RUNTIME) runtimeHandle: ContainerHandle,
      @InjectClient ingressClient: Client,
  ) =
      runTest(timeout = 120.seconds) {
        concurrentBlocks.set(0)
        maxConcurrentBlocks.set(0)

        val runId = UUID.randomUUID().toString().take(8)
        val scope = "myscope-$runId"
        val limit = 2
        val invocationCount = 4
        val blockerKeys = (0 until invocationCount).map { "block-key-$runId-$it" }

        val ruleVersion =
            upsertConcurrencyRule(adminURI, pattern = scope, concurrency = limit).version
        awaitRuleBookApplied(runtimeHandle, ruleVersion)

        val outerIds =
            blockerKeys.map { key ->
              sendInvocationWithScope(
                  ingressURI, scope, "BlockingProxy", "block", Json.encodeToString(key))
            }

        // Wait until exactly `limit` scoped invocations are running; the rest are held by the rule.
        //
        // We count the scoped BlockingProxy invocations directly instead of the downstream Blocker
        // invocations they spawn. The runtime only guarantees that no more than `limit` scoped
        // invocations run *concurrently* - it does not guarantee that it is always the *same* set.
        // Under a leadership change a running BlockingProxy can yield its slot to a held one, but
        // the
        // Blocker invocation it already spawned keeps running until its awakeable is resolved. The
        // Blocker count is therefore cumulative and can exceed `limit` even while the limit is
        // respected, which made the previous `Blocker/%/run` count flaky on threeNodes.
        // See https://github.com/restatedev/e2e/issues/435.
        val runningScopedFilter =
            "target_service_name = 'BlockingProxy' AND scope = '$scope' AND status = 'running'"
        await withAlias
            "exactly $limit scoped invocations are running" untilAsserted
            {
              assertThat(getAllInvocations(adminURI, runningScopedFilter)).hasSize(limit)
            }

        // Resolve one awakeable at a time. After each resolve, a held outer becomes running and
        // spawns its Blocker.
        val unresolvedKeys = blockerKeys.toMutableSet()
        repeat(invocationCount) {
          var activeKey: String? = null
          await withAlias
              "find a Blocker (among unresolved keys) that has registered its awakeable" untilAsserted
              {
                val found =
                    unresolvedKeys.firstNotNullOfOrNull { key ->
                      val awkId =
                          ingressClient
                              .toVirtualObject<Blocker>(key)
                              .request { getAwakeable() }
                              .options(idempotentCallOptions)
                              .call()
                              .response
                      if (awkId.isNotEmpty()) key else null
                    }
                assertThat(found).isNotNull
                activeKey = found
              }

          ingressClient
              .toVirtualObject<Blocker>(activeKey!!)
              .request { resolveAwakeable("done") }
              .options(idempotentCallOptions)
              .call()
          unresolvedKeys.remove(activeKey!!)
        }

        // All four outer BlockingProxy invocations must complete successfully.
        outerIds.forEach { outerId ->
          val response =
              ingressClient
                  .invocationHandle(outerId, TypeTag.of(String::class.java))
                  .attachSuspend()
                  .response
          assertThat(response).isEqualTo("done")
        }

        // The concurrency limit must also hold for actual handler execution: at no point during the
        // run should more than `limit` BlockingProxy handlers have run concurrently on the SDK
        // endpoint. This maximum is accumulated across the whole run.
        //
        // This strict in-process bound is why the test is single-node only: on multi-node a
        // leadership change can momentarily run more than `limit` handlers at once (the old leader
        // needs time to notice it lost leadership and the SDK deployment needs time to abort its
        // in-flight invocations, while the new leader has already dispatched replacements).
        // Single-node clusters have no leadership changes, so vqueue admission control is
        // authoritative. See https://github.com/restatedev/e2e/issues/435.
        assertThat(maxConcurrentBlocks.get())
            .`as`("max concurrently running BlockingProxy handlers")
            .isLessThanOrEqualTo(limit)

        bulkDeleteRules(adminURI, listOf(scope))
      }
}
