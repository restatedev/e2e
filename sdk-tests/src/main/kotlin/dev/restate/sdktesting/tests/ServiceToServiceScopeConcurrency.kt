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
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toService
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
import dev.restate.sdktesting.contracts.Proxy
import dev.restate.sdktesting.infra.*
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies that the SDK under test propagates the `scope`/`limitKey` service-to-service call
 * options to the runtime: a rule-book concurrency rule scoped to `scope/limitKey` must limit the
 * invocations the SDK issues with those options.
 *
 * The behaviour under test lives entirely in the SDK container: [Proxy.manyCalls] fans out N
 * background calls to [BlockingProxy.block], each carrying the same `scope`/`limitKey`. The
 * *target* of those scoped calls is deliberately hosted **in the test JVM** (not in the SDK
 * container) so we can instrument it: [BlockingProxy.block] counts, via in-process atomics, how
 * many of these handlers execute concurrently. Each `block` makes a regular (unscoped) call to a
 * distinct [Blocker] virtual object that blocks on an awakeable, so a `block` invocation holds its
 * rule slot until the awakeable is resolved.
 *
 * Matching the rule against the exact `scope/limitKey` pattern means the test fails unless *both*
 * options are propagated: if either were dropped, the invocation identity would not match the rule
 * and all N would run concurrently (the running count would be N, and on single-node the strict
 * in-process bound would exceed `limit`).
 *
 * Runs only on single-node suites: the strict in-process concurrency bound (see
 * [scopeAndLimitKeyArePropagatedOnServiceToServiceCalls]) cannot be asserted on multi-node
 * clusters, where a leadership change can momentarily run more than `limit` handlers at once. See
 * https://github.com/restatedev/e2e/pull/436.
 */
class ServiceToServiceScopeConcurrency {

  /**
   * Scoped target of [Proxy.manyCalls], hosted in the test JVM so we can measure how many of these
   * handlers run concurrently. The scope concurrency limit is enforced on these invocations.
   */
  @Service
  @Name("BlockingProxy")
  class BlockingProxy {
    @Handler
    suspend fun block(key: String): String {
      // The counter is incremented on entry and decremented in `finally`, which also runs when the
      // SDK suspends the invocation at the `.await()` below (coroutine cancellation) and on
      // completion. We keep the running maximum so the test can assert the runtime never lets more
      // than `limit` of these handlers run at once - even if leadership changes shuffle *which*
      // invocations hold the slots.
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
      val result = awk.await()
      state().set("result", result)
      return result
    }

    @Shared suspend fun getAwakeable(): String = state().get<String>("awk") ?: ""

    @Shared suspend fun getResult(): String = state().get<String>("result") ?: ""

    @Shared
    suspend fun resolveAwakeable(value: String) {
      val id = state().get<String>("awk") ?: ""
      if (id.isEmpty()) throw TerminalException("Awakeable not registered yet")
      awakeableHandle(id).resolve(value)
    }
  }

  companion object {
    private const val BLOCKING_PROXY_NAME = "BlockingProxy"

    /** Number of [BlockingProxy.block] handlers currently executing on the in-process endpoint. */
    val concurrentBlocks = AtomicInteger(0)
    /** Running maximum of [concurrentBlocks] observed during a test run. */
    val maxConcurrentBlocks = AtomicInteger(0)

    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      // Only Proxy runs in the SDK under test: its `manyCalls` is what must propagate
      // scope/limitKey.
      withServiceSpec(ServiceSpec.defaultBuilder().withServices(Proxy::class))
      // The scoped target + blocker run in the test JVM so we can measure concurrency directly.
      withEndpoint(Endpoint.bind(BlockingProxy()).bind(Blocker()))
      // Scoped invocations and rule-book require the vqueues experimental feature.
      // TODO: drop this once the minimum supported Restate version is v1.8, where vqueues is on by
      //   default.
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_VQUEUES", "true")
      // Passing scope/limitKey on a service-to-service call requires invocation protocol v7.
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_PROTOCOL_V7", "true")
      // Reduce rule-book activation latency so the test isn't gated on the default 30s poll.
      withEnv("RESTATE_WORKER__RULE_BOOK_POLL_INTERVAL", "1s")
    }
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  @DisplayName(
      "Concurrency limit is enforced on service-to-service calls carrying scope and limit key")
  fun scopeAndLimitKeyArePropagatedOnServiceToServiceCalls(
      @InjectAdminURI adminURI: URI,
      @InjectContainerHandle(hostName = RESTATE_RUNTIME) runtimeHandle: ContainerHandle,
      @InjectClient ingressClient: Client,
  ) =
      runTest(timeout = 120.seconds) {
        concurrentBlocks.set(0)
        maxConcurrentBlocks.set(0)

        val runId = UUID.randomUUID().toString().take(8)
        val scope = "s2s-scope-$runId"
        val limitKey = "s2s-limitkey-$runId"
        // The runtime matches the rule pattern against the "scope/limitKey" identity, so an
        // exact-match rule fails unless BOTH options propagate.
        val rulePattern = "$scope/$limitKey"
        val limit = 2
        val invocationCount = 4
        val blockerKeys = (0 until invocationCount).map { "blocker-$runId-$it" }

        val ruleVersion =
            upsertConcurrencyRule(adminURI, pattern = rulePattern, concurrency = limit).version
        awaitRuleBookApplied(runtimeHandle, ruleVersion)

        // A single (unscoped) Proxy invocation fans out one scoped background call to
        // BlockingProxy.block per blocker key. Each scoped block invocation is what the rule
        // limits;
        // once running, it makes a regular (unscoped) call to its Blocker, which blocks on an
        // awakeable.
        ingressClient
            .toService<Proxy>()
            .request {
              manyCalls(
                  blockerKeys.map { key ->
                    Proxy.ManyCallRequest(
                        // Outer, scoped call to the JVM-hosted BlockingProxy service.
                        Proxy.ProxyRequest(
                            serviceName = BLOCKING_PROXY_NAME,
                            virtualObjectKey = null,
                            handlerName = "block",
                            message = Json.encodeToString(key).encodeToByteArray(),
                            scope = scope,
                            limitKey = limitKey,
                        ),
                        oneWayCall = true,
                        awaitAtTheEnd = false,
                    )
                  })
            }
            .options(idempotentCallOptions)
            .call()

        // The rule must hold the count of running scoped invocations at exactly `limit`; the rest
        // wait.
        //
        // Count the scoped BlockingProxy invocations directly (status = running) rather than the
        // downstream Blockers they call. The runtime only guarantees that no more than `limit`
        // scoped
        // invocations run *concurrently* - not that it is always the *same* set. Under a leadership
        // change a running block can yield its slot to a held one, but the Blocker invocation it
        // already spawned keeps running (its awakeable stays registered) until resolved. Counting
        // Blockers is therefore cumulative and can exceed `limit` even while the limit is
        // respected,
        // which made the analogous e2e ConcurrencyLimitTest flaky on multi-node.
        // See https://github.com/restatedev/e2e/pull/436.
        val runningScopedFilter =
            "target_service_name = '$BLOCKING_PROXY_NAME' AND scope = '$scope' AND status = 'running'"
        await withAlias
            "exactly $limit scoped invocations are running" untilAsserted
            {
              assertThat(getAllInvocations(adminURI, runningScopedFilter)).hasSize(limit)
            }

        // Resolve one awakeable at a time. After each resolve, a held block invocation becomes
        // running and spawns its Blocker, which registers its own awakeable.
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

        // Every Blocker must eventually complete, recording the resolved value. Since BlockingProxy
        // awaits its Blocker, a recorded result also means the scoped block invocation ran.
        blockerKeys.forEach { key ->
          await withAlias
              "blocker $key completed" untilAsserted
              {
                assertThat(
                        ingressClient
                            .toVirtualObject<Blocker>(key)
                            .request { getResult() }
                            .options(idempotentCallOptions)
                            .call()
                            .response)
                    .isEqualTo("done")
              }
        }

        // The concurrency limit must also hold for actual handler execution: at no point during the
        // run should more than `limit` BlockingProxy handlers have run concurrently in the test
        // JVM.
        // This maximum is accumulated across the whole run.
        //
        // This strict in-process bound is why the test is single-node only: on multi-node a
        // leadership change can momentarily run more than `limit` handlers at once (the old leader
        // needs time to notice it lost leadership and abort its in-flight invocations, while the
        // new
        // leader has already dispatched replacements). Single-node clusters have no leadership
        // changes, so vqueue admission control is authoritative.
        // See https://github.com/restatedev/e2e/pull/436.
        assertThat(maxConcurrentBlocks.get())
            .`as`("max concurrently running BlockingProxy handlers")
            .isLessThanOrEqualTo(limit)

        bulkDeleteRules(adminURI, listOf(rulePattern))
      }
}
