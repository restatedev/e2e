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
import dev.restate.client.SendResponse.SendStatus
import dev.restate.client.kotlin.getOutputSuspend
import dev.restate.client.kotlin.response
import dev.restate.common.Target
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.SharedObjectContext
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.kotlin.endpoint.idempotencyRetention
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.resolve
import dev.restate.sdk.kotlin.send
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.stateKey
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.tests.IdempotentInvocationTest.Counter.CounterUpdateResponse
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class IdempotentInvocationTest {

  @VirtualObject
  @Name("Counter")
  class Counter {
    companion object {
      private val COUNTER_KEY: StateKey<Long> = stateKey<Long>("counter")
    }

    @Serializable data class CounterUpdateResponse(val oldValue: Long, val newValue: Long)

    @Handler
    suspend fun add(context: ObjectContext, value: Long): CounterUpdateResponse {
      val oldCount: Long = context.get(COUNTER_KEY) ?: 0L
      val newCount = oldCount + value
      context.set(COUNTER_KEY, newCount)

      return CounterUpdateResponse(oldCount, newCount)
    }

    @Handler
    @Shared
    suspend fun get(context: SharedObjectContext): Long = context.get(COUNTER_KEY) ?: 0L
  }

  @Service
  class CounterProxy {
    @Serializable data class ProxyRequest(val key: String, val value: Long)

    @Handler
    suspend fun proxyThrough(context: Context, request: ProxyRequest) {
      IdempotentInvocationTestCounterHandlers.add(request.key, request.value).send(context)
    }
  }

  @VirtualObject
  class AwakeableHolder {

    @Handler
    suspend fun run(ctx: ObjectContext): String {
      val awk = ctx.awakeable<String>()
      ctx.set<String>("awk", awk.id)
      return awk.await()
    }

    @Shared
    suspend fun getAwakeable(ctx: SharedObjectContext): String = ctx.get<String>("awk") ?: ""

    @Shared
    suspend fun resolveAwakeable(ctx: SharedObjectContext, response: String) {
      val awkKey = ctx.get<String>("awk")
      if (awkKey.isNullOrEmpty()) {
        throw TerminalException("Expected awakeable to be non null")
      }
      ctx.awakeableHandle(awkKey).resolve(response)
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(
          Endpoint.bind(
                  Counter(),
                  {
                    it.idempotencyRetention = 5.seconds
                    it.configureHandler("get") {
                      it.idempotencyRetention = 0.seconds
                      it.journalRetention = 0.seconds
                    }
                  })
              .bind(CounterProxy())
              .bind(AwakeableHolder()))
      // We need the short cleanup interval b/c of the tests with the idempotent invoke.
      withEnv("RESTATE_WORKER__CLEANUP_INTERVAL", "1s")
    }
  }

  @Test
  @DisplayName("Idempotent invocation to a virtual object")
  fun idempotentInvokeVirtualObject(@InjectClient ingressClient: Client) = runTest {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val counterClient =
        IdempotentInvocationTestCounterClient.fromClient(ingressClient, counterRandomName)

    // First call updates the value
    val firstResponse = counterClient.add(2) { idempotencyKey = myIdempotencyId }
    assertThat(firstResponse)
        .returns(0, CounterUpdateResponse::oldValue)
        .returns(2, CounterUpdateResponse::newValue)

    // Next call returns the same value
    val secondResponse = counterClient.add(2) { idempotencyKey = myIdempotencyId }
    assertThat(secondResponse)
        .returns(0L, CounterUpdateResponse::oldValue)
        .returns(2L, CounterUpdateResponse::newValue)

    // Await until the idempotency id is cleaned up and the next idempotency call updates the
    // counter again
    await withAlias
        "cleanup of the previous idempotent request" withTimeout
        20.seconds untilAsserted
        {
          assertThat(counterClient.add(2) { idempotencyKey = myIdempotencyId })
              .returns(2, CounterUpdateResponse::oldValue)
              .returns(4, CounterUpdateResponse::newValue)
        }

    // State in the counter service is now equal to 4
    await withAlias
        "Get returns 4 now" untilAsserted
        {
          assertThat(counterClient.get()).isEqualTo(4L)
        }
  }

  @Test
  @DisplayName("Idempotent invocation to a service")
  fun idempotentInvokeService(@InjectClient ingressClient: Client) = runTest {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val counterClient =
        IdempotentInvocationTestCounterClient.fromClient(ingressClient, counterRandomName)
    val proxyCounterClient = IdempotentInvocationTestCounterProxyClient.fromClient(ingressClient)

    // Send request twice with same idempotency key. Should proxy the request only once!
    proxyCounterClient.proxyThrough(CounterProxy.ProxyRequest(counterRandomName, 2)) {
      idempotencyKey = myIdempotencyId
    }
    proxyCounterClient.proxyThrough(CounterProxy.ProxyRequest(counterRandomName, 2)) {
      idempotencyKey = myIdempotencyId
    }

    // Wait for get
    await untilAsserted { assertThat(counterClient.get()).isEqualTo(2) }

    // Hitting directly the counter client should be executed immediately and return 4
    assertThat(counterClient.add(2, idempotentCallOptions))
        .returns(2, CounterUpdateResponse::oldValue)
        .returns(4, CounterUpdateResponse::newValue)
  }

  @Test
  @DisplayName("Idempotent invocation to a virtual object using send")
  fun idempotentInvokeSend(@InjectClient ingressClient: Client) = runTest {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val counterClient =
        IdempotentInvocationTestCounterClient.fromClient(ingressClient, counterRandomName)

    // Send request twice with same idempotency key
    val firstInvocationSendStatus = counterClient.send().add(2) { idempotencyKey = myIdempotencyId }
    assertThat(firstInvocationSendStatus.sendStatus()).isEqualTo(SendStatus.ACCEPTED)
    val secondInvocationSendStatus =
        counterClient.send().add(2) { idempotencyKey = myIdempotencyId }
    assertThat(secondInvocationSendStatus.sendStatus()).isEqualTo(SendStatus.PREVIOUSLY_ACCEPTED)

    // IDs should be the same
    assertThat(firstInvocationSendStatus.invocationId())
        .startsWith("inv")
        .isEqualTo(secondInvocationSendStatus.invocationId())

    // Wait for get
    await untilAsserted { assertThat(counterClient.get()).isEqualTo(2) }

    // Changing idempotency key should be executed immediately and return 4
    assertThat(counterClient.add(2, idempotentCallOptions))
        .returns(2, CounterUpdateResponse::oldValue)
        .returns(4, CounterUpdateResponse::newValue)
  }

  @Test
  @DisplayName("Idempotent send then attach/getOutput with idempotency key")
  fun idempotentSendThenAttachWIthIdempotencyKey(@InjectClient ingressClient: Client) = runTest {
    val myIdempotencyId = UUID.randomUUID().toString()
    val response = "response"
    val interpreterId = UUID.randomUUID().toString()

    // Send request
    val awakeableHolder =
        IdempotentInvocationTestAwakeableHolderClient.fromClient(ingressClient, interpreterId)
    assertThat(awakeableHolder.send().run { idempotencyKey = myIdempotencyId }.sendStatus())
        .isEqualTo(SendStatus.ACCEPTED)

    val invocationHandle =
        ingressClient.idempotentInvocationHandle(
            Target.virtualObject(
                IdempotentInvocationTestAwakeableHolderHandlers.Metadata.SERVICE_NAME,
                interpreterId,
                "run"),
            myIdempotencyId,
            IdempotentInvocationTestAwakeableHolderHandlers.Metadata.Serde.RUN_OUTPUT)

    // Attach to request
    val blockedFut = invocationHandle.attachAsync()

    // Output is not ready yet
    assertThat(invocationHandle.getOutputSuspend().response.isReady).isFalse()

    // Blocked fut should still be blocked
    assertThat(blockedFut).isNotDone

    // Unblock
    await withAlias
        "sync point" untilAsserted
        {
          assertThat(awakeableHolder.getAwakeable()).isNotBlank
        }
    awakeableHolder.resolveAwakeable(response, idempotentCallOptions)

    // Attach should be completed
    assertThat(blockedFut.await().response).isEqualTo(response)

    // Invoke get output
    assertThat(invocationHandle.getOutputSuspend().response().value).isEqualTo(response)
  }
}
