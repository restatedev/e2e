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
import dev.restate.client.kotlin.rejectSuspend
import dev.restate.client.kotlin.resolveSuspend
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.serde.kotlinx.jsonSerde
import java.util.UUID
import kotlinx.coroutines.async
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AwakeableIngressEndpoint {

  @VirtualObject
  class MyService {

    @Handler
    suspend fun run(ctx: ObjectContext): String {
      val awk = ctx.awakeable<String>()
      ctx.set<String>("awk", awk.id)
      return awk.await()
    }

    @Shared
    suspend fun getAwakeable(ctx: SharedObjectContext): String = ctx.get<String>("awk") ?: ""
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(MyService()))
    }
  }

  @Test
  fun completeWithSuccess(@InjectClient ingressClient: Client) = runTest {
    val key = UUID.randomUUID().toString()
    val client = AwakeableIngressEndpointMyServiceClient.fromClient(ingressClient, key)

    val runResult = async { client.run(idempotentCallOptions) }

    // Wait for awakeable to be registered
    await withAlias
        "awakeable is registered" untilAsserted
        {
          assertThat(client.getAwakeable()).isNotBlank()
        }

    val awakeableId = client.getAwakeable(idempotentCallOptions)

    val expectedResult = "solved!"

    await withAlias
        "resolve awakeable" untilAsserted
        {
          ingressClient.awakeableHandle(awakeableId).resolveSuspend(jsonSerde(), expectedResult)
        }

    assertThat(runResult.await()).isEqualTo(expectedResult)
  }

  @Test
  fun completeWithFailure(@InjectClient ingressClient: Client) = runTest {
    val key = UUID.randomUUID().toString()
    val client = AwakeableIngressEndpointMyServiceClient.fromClient(ingressClient, key)

    val runResult = async { runCatching { client.run(idempotentCallOptions) }.exceptionOrNull() }

    // Wait for awakeable to be registered
    await withAlias
        "awakeable is registered" untilAsserted
        {
          assertThat(client.getAwakeable()).isNotBlank()
        }

    val awakeableId = client.getAwakeable(idempotentCallOptions)

    val expectedReason = "my bad!"

    await withAlias
        "reject awakeable" untilAsserted
        {
          ingressClient.awakeableHandle(awakeableId).rejectSuspend(expectedReason)
        }

    assertThat(runResult.await()).hasMessageContaining(expectedReason)
  }
}
