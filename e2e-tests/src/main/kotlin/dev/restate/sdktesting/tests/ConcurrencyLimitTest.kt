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
import dev.restate.sdk.kotlin.call
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.resolve
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdk.kotlin.toVirtualObject
import dev.restate.sdktesting.infra.*
import dev.restate.serde.TypeTag
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies the runtime enforces the rule-book action-concurrency limit on a scope. Scoped
 * invocations are limited to N in flight, and the held excess progresses as the running ones
 * complete.
 */
class ConcurrencyLimitTest {

  @Service
  @Name("BlockingProxy")
  class BlockingProxy {
    @Handler
    suspend fun block(key: String): String =
        toVirtualObject<Blocker>(key).request { run() }.call().await()
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
  @DisplayName(
      "Action concurrency limit on a scope holds excess invocations and releases on completion")
  fun actionConcurrencyLimitIsRespected(
      @InjectIngressURI ingressURI: URI,
      @InjectAdminURI adminURI: URI,
      @InjectClient ingressClient: Client,
  ) =
      runTest(timeout = 120.seconds) {
        val runId = UUID.randomUUID().toString().take(8)
        val scope = "myscope-$runId"
        val limit = 2
        val invocationCount = 4
        val blockerKeys = (0 until invocationCount).map { "block-key-$runId-$it" }

        upsertActionConcurrencyRule(adminURI, pattern = scope, actionConcurrency = limit)

        val outerIds =
            blockerKeys.map { key ->
              sendInvocationWithScope(
                  ingressURI, scope, "BlockingProxy", "block", Json.encodeToString(key))
            }

        val blockerTargetFilter = "target LIKE 'Blocker/%/run'"

        // Wait until exactly `limit` Blocker invocations exist; the rest are held by the rule.
        await withAlias
            "exactly $limit Blocker invocations are in flight" untilAsserted
            {
              assertThat(getAllInvocations(adminURI, blockerTargetFilter)).hasSize(limit)
            }
        // Briefly confirm the count stays at `limit`, defending against a slow rule activation
        // where all 4 would have started before our first check.
        delay(1.seconds)
        assertThat(getAllInvocations(adminURI, blockerTargetFilter)).hasSize(limit)

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

        bulkDeleteRules(adminURI, listOf(scope))
      }
}
