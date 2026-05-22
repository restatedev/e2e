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
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdktesting.infra.*
import dev.restate.serde.TypeTag
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Verifies that scope is part of an invocation's identity: the same idempotency key sent in two
 * different scopes produces two distinct invocations rather than colliding on dedup. This is a
 * runtime guarantee independent of any rule-book rule.
 */
class ScopeIsolationTest {

  @Service
  @Name("Random")
  class Random {
    @Handler suspend fun genRandomUUID(input: String) = runBlock { UUID.randomUUID().toString() }
  }

  companion object {
    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(Random()))
      // Scoped invocations require the vqueues experimental feature.
      // TODO: drop this once the minimum supported Restate version is v1.8,
      //  where vqueues are enabled by default.
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_VQUEUES", "true")
    }
  }

  @Test
  @DisplayName("Same idempotency key in two different scopes produces two distinct invocations")
  fun sameIdempotencyKeyAcrossScopesIsolates(
      @InjectIngressURI ingressURI: URI,
      @InjectClient ingressClient: Client,
  ) = runTest {
    val sharedIdempotencyKey = "shared-idempotency"
    val scopeA = UUID.randomUUID().toString()
    val scopeB = UUID.randomUUID().toString()
    val body = Json.encodeToString("foo")

    val idA =
        sendInvocationWithScope(
            ingressURI,
            scopeA,
            "Random",
            "genRandomUUID",
            body,
            idempotencyKey = sharedIdempotencyKey)
    val idB =
        sendInvocationWithScope(
            ingressURI,
            scopeB,
            "Random",
            "genRandomUUID",
            body,
            idempotencyKey = sharedIdempotencyKey)

    assertThat(idA).isNotEqualTo(idB)

    // Each invocation generates its own random value via `runBlock`. If the runtime had
    // collapsed the two calls under shared idempotency, both responses would be identical;
    // distinct values prove the scope isolated them.
    val respA =
        ingressClient.invocationHandle(idA, TypeTag.of(String::class.java)).attachSuspend().response
    val respB =
        ingressClient.invocationHandle(idB, TypeTag.of(String::class.java)).attachSuspend().response
    assertThat(respA).isNotEqualTo(respB)
  }
}
