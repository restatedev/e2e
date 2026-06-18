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
import dev.restate.common.reflections.ReflectionUtils.extractServiceName
import dev.restate.sdktesting.contracts.Proxy
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitOne
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.CreateAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.InterpretRequest
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.ResolveAwakeable
import dev.restate.sdktesting.infra.*
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies that the new service-to-service call options for `scope` and `limitKey` are propagated
 * to the runtime: a rule-book concurrency rule scoped to `scope/limitKey` must limit the
 * invocations a service issues with those options.
 *
 * Scope is only supported for `@Service` targets, so the scoped invocations are calls to
 * [Proxy.call] (a service). A single, unscoped `Proxy.manyCalls` fans out N background calls to
 * `Proxy.call`, each carrying the same `scope`/`limitKey`. Each of those inner `Proxy.call`
 * invocations then makes a regular (unscoped) call to a distinct [VirtualObjectCommandInterpreter]
 * key that blocks on an awakeable. So an interpreter key has registered its awakeable exactly when
 * its `Proxy.call` is actually running. The rule caps the number running at `limit`; the rest are
 * held and progress as running ones complete.
 *
 * Matching the rule against the exact `scope/limitKey` pattern means the test fails unless *both*
 * options are propagated: if either were dropped, the invocation identity would not match the rule
 * and all N would run concurrently.
 */
class ServiceToServiceScopeConcurrency {

  companion object {
    private const val AWAKEABLE_KEY = "block"

    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(
          ServiceSpec.defaultBuilder()
              .withServices(Proxy::class, VirtualObjectCommandInterpreter::class))
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
  @DisplayName(
      "Concurrency limit is enforced on service-to-service calls carrying scope and limit key")
  fun scopeAndLimitKeyArePropagatedOnServiceToServiceCalls(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerHandle(hostName = RESTATE_RUNTIME) runtimeHandle: ContainerHandle,
  ) = runTest {
    val runId = UUID.randomUUID().toString().take(8)
    val scope = "s2s-scope-$runId"
    val limitKey = "s2s-limitkey-$runId"
    // The runtime matches the rule pattern against the "scope/limitKey" identity.
    val rulePattern = "$scope/$limitKey"
    val limit = 2
    val invocationCount = 4
    val interpreterKeys = (0 until invocationCount).map { "interpreter-$runId-$it" }

    val ruleVersion =
        upsertConcurrencyRule(adminURI, pattern = rulePattern, concurrency = limit).version
    awaitRuleBookApplied(runtimeHandle, ruleVersion)

    // A single (unscoped) Proxy invocation fans out one scoped background call to Proxy.call per
    // interpreter key. Each scoped Proxy.call is an invocation the rule limits; once running, it
    // makes a regular (unscoped) call to its interpreter, which blocks on an awakeable.
    val proxyName = extractServiceName(Proxy::class.java)
    val interpreterName = extractServiceName(VirtualObjectCommandInterpreter::class.java)
    val blockCommand =
        Json.encodeToString(InterpretRequest(listOf(AwaitOne(CreateAwakeable(AWAKEABLE_KEY)))))
            .encodeToByteArray()
    ingressClient
        .toService<Proxy>()
        .request {
          manyCalls(
              interpreterKeys.map { key ->
                // Inner, unscoped call from Proxy.call to the blocking interpreter VO.
                val innerCall =
                    Proxy.ProxyRequest(
                        serviceName = interpreterName,
                        virtualObjectKey = key,
                        handlerName = "interpretCommands",
                        message = blockCommand,
                    )
                Proxy.ManyCallRequest(
                    // Outer, scoped call to the Proxy service.
                    Proxy.ProxyRequest(
                        serviceName = proxyName,
                        virtualObjectKey = null,
                        handlerName = "call",
                        message = Json.encodeToString(innerCall).encodeToByteArray(),
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

    suspend fun isRunning(key: String): Boolean =
        ingressClient
            .toVirtualObject<VirtualObjectCommandInterpreter>(key)
            .request { hasAwakeable(AWAKEABLE_KEY) }
            .options(idempotentCallOptions)
            .call()
            .response

    // The rule must hold the count of running invocations at exactly `limit`; the rest wait.
    await withAlias
        "exactly $limit scoped invocations are running" untilAsserted
        {
          assertThat(interpreterKeys.count { isRunning(it) }).isEqualTo(limit)
        }

    // Resolve one awakeable at a time. After each resolve, a held invocation becomes running
    // and registers its own awakeable.
    val unresolvedKeys = interpreterKeys.toMutableSet()
    repeat(invocationCount) {
      var activeKey: String? = null
      await withAlias
          "find a running interpreter (among unresolved keys)" untilAsserted
          {
            val found = unresolvedKeys.firstOrNull { isRunning(it) }
            assertThat(found).isNotNull
            activeKey = found
          }

      ingressClient
          .toVirtualObject<VirtualObjectCommandInterpreter>(activeKey!!)
          .request { resolveAwakeable(ResolveAwakeable(AWAKEABLE_KEY, "done")) }
          .options(idempotentCallOptions)
          .call()
      unresolvedKeys.remove(activeKey!!)
    }

    // Every interpreter invocation must eventually complete, recording the resolved value.
    interpreterKeys.forEach { key ->
      await withAlias
          "interpreter $key completed" untilAsserted
          {
            assertThat(
                    ingressClient
                        .toVirtualObject<VirtualObjectCommandInterpreter>(key)
                        .request { getResults() }
                        .options(idempotentCallOptions)
                        .call()
                        .response)
                .contains("done")
          }
    }

    bulkDeleteRules(adminURI, listOf(rulePattern))
  }
}
