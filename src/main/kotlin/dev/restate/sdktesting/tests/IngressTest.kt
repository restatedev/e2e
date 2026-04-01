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
import dev.restate.admin.model.ModifyServiceRequest
import dev.restate.client.Client
import dev.restate.client.IngressException
import dev.restate.client.SendResponse.SendStatus
import dev.restate.client.kotlin.getOutputSuspend
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toService
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.common.Target
import dev.restate.common.reflections.ReflectionUtils.extractServiceName
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.kotlin.awakeableHandle
import dev.restate.sdk.kotlin.call
import dev.restate.sdk.kotlin.endpoint.idempotencyRetention
import dev.restate.sdk.kotlin.endpoint.ingressPrivate
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.resolve
import dev.restate.sdk.kotlin.send
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdk.kotlin.stateKey
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.tests.IngressTest.Counter.CounterUpdateResponse
import dev.restate.serde.TypeTag
import java.net.URI
import java.util.*
import kotlin.text.get
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.InstanceOfAssertFactories
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Assorted tests checking the ingress behavior */
class IngressTest {

  @VirtualObject
  @Name("Counter")
  class Counter {
    companion object {
      private val COUNTER_KEY: StateKey<Long> = stateKey<Long>("counter")
    }

    @Serializable data class CounterUpdateResponse(val oldValue: Long, val newValue: Long)

    @Handler
    suspend fun add(value: Long): CounterUpdateResponse {
      val oldCount: Long = state().get(COUNTER_KEY) ?: 0L
      val newCount = oldCount + value
      state().set(COUNTER_KEY, newCount)

      return CounterUpdateResponse(oldCount, newCount)
    }

    @Handler @Shared suspend fun get(): Long = state().get(COUNTER_KEY) ?: 0L
  }

  @Service
  class CounterProxy {
    @Serializable data class ProxyRequest(val key: String, val value: Long)

    @Handler
    suspend fun proxyThrough(request: ProxyRequest) {
      dev.restate.sdk.kotlin
          .toVirtualObject<Counter>(request.key)
          .request { add(request.value) }
          .send()
    }
  }

  @VirtualObject
  class AwakeableHolder {

    @Handler
    suspend fun run(): String {
      val awk = awakeable<String>()
      state().set("awk", awk.id)
      return awk.await()
    }

    @Shared suspend fun getAwakeable(): String = state().get<String>("awk") ?: ""

    @Shared
    suspend fun resolveAwakeable(response: String) {
      val awkKey = state().get<String>("awk")
      if (awkKey.isNullOrEmpty()) {
        throw TerminalException("Expected awakeable to be non null")
      }
      awakeableHandle(awkKey).resolve(response)
    }
  }

  @Service
  class PrivateGreeter {
    @Handler fun greet(name: String) = "Hello $name"
  }

  @Service
  class ProxyGreeter {
    @Handler
    suspend fun greet(name: String): String =
        dev.restate.sdk.kotlin.toService<PrivateGreeter>().request { greet(name) }.call().await()
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
              .bind(AwakeableHolder())
              .bind(PrivateGreeter(), { it.ingressPrivate = true })
              .bind(ProxyGreeter()))

      // We need the short cleanup interval b/c of the tests with the idempotent invoke.
      withEnv("RESTATE_WORKER__CLEANUP_INTERVAL", "1s")
    }
  }

  @Test
  @DisplayName("Idempotent invocation to a virtual object")
  fun idempotentInvokeVirtualObject(@InjectClient ingressClient: Client) = runTest {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val counterClient = ingressClient.toVirtualObject<Counter>(counterRandomName)

    // First call updates the value
    val firstResponse =
        counterClient
            .request { add(2) }
            .options { idempotencyKey = myIdempotencyId }
            .call()
            .response
    assertThat(firstResponse)
        .returns(0, CounterUpdateResponse::oldValue)
        .returns(2, CounterUpdateResponse::newValue)

    // Next call returns the same value
    val secondResponse =
        counterClient
            .request { add(2) }
            .options { idempotencyKey = myIdempotencyId }
            .call()
            .response
    assertThat(secondResponse)
        .returns(0L, CounterUpdateResponse::oldValue)
        .returns(2L, CounterUpdateResponse::newValue)

    // Await until the idempotency id is cleaned up and the next idempotency call updates the
    // counter again
    await withAlias
        "cleanup of the previous idempotent request" withTimeout
        20.seconds untilAsserted
        {
          assertThat(
                  counterClient
                      .request { add(2) }
                      .options { idempotencyKey = myIdempotencyId }
                      .call()
                      .response)
              .returns(2, CounterUpdateResponse::oldValue)
              .returns(4, CounterUpdateResponse::newValue)
        }

    // State in the counter service is now equal to 4
    await withAlias
        "Get returns 4 now" untilAsserted
        {
          assertThat(counterClient.request { get() }.call().response).isEqualTo(4L)
        }
  }

  @Test
  @DisplayName("Idempotent invocation to a service")
  fun idempotentInvokeService(@InjectClient ingressClient: Client) = runTest {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val counterClient = ingressClient.toVirtualObject<Counter>(counterRandomName)
    val proxyCounterClient = ingressClient.toService<CounterProxy>()

    // Send request twice with same idempotency key. Should proxy the request only once!
    proxyCounterClient
        .request { proxyThrough(CounterProxy.ProxyRequest(counterRandomName, 2)) }
        .options { idempotencyKey = myIdempotencyId }
        .call()
        .response
    proxyCounterClient
        .request { proxyThrough(CounterProxy.ProxyRequest(counterRandomName, 2)) }
        .options { idempotencyKey = myIdempotencyId }
        .call()
        .response

    // Wait for get
    await untilAsserted { assertThat(counterClient.request { get() }.call().response).isEqualTo(2) }

    // Hitting directly the counter client should be executed immediately and return 4
    assertThat(counterClient.request { add(2) }.options(idempotentCallOptions).call().response)
        .returns(2, CounterUpdateResponse::oldValue)
        .returns(4, CounterUpdateResponse::newValue)
  }

  @Test
  @DisplayName("Idempotent invocation to a virtual object using send")
  fun idempotentInvokeSend(@InjectClient ingressClient: Client) = runTest {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val counterClient = ingressClient.toVirtualObject<Counter>(counterRandomName)

    // Send request twice with same idempotency key
    val firstInvocationSendStatus =
        counterClient.request { add(2) }.options { idempotencyKey = myIdempotencyId }.send()
    assertThat(firstInvocationSendStatus.sendStatus()).isEqualTo(SendStatus.ACCEPTED)
    val secondInvocationSendStatus =
        counterClient.request { add(2) }.options { idempotencyKey = myIdempotencyId }.send()
    assertThat(secondInvocationSendStatus.sendStatus()).isEqualTo(SendStatus.PREVIOUSLY_ACCEPTED)

    // IDs should be the same
    assertThat(firstInvocationSendStatus.invocationId())
        .startsWith("inv")
        .isEqualTo(secondInvocationSendStatus.invocationId())

    // Wait for get
    await untilAsserted { assertThat(counterClient.request { get() }.call().response).isEqualTo(2) }

    // Changing idempotency key should be executed immediately and return 4
    assertThat(counterClient.request { add(2) }.options(idempotentCallOptions).call().response)
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
    val awakeableHolder = ingressClient.toVirtualObject<AwakeableHolder>(interpreterId)
    assertThat(
            awakeableHolder
                .request { run() }
                .options { idempotencyKey = myIdempotencyId }
                .send()
                .sendStatus())
        .isEqualTo(SendStatus.ACCEPTED)

    val invocationHandle =
        ingressClient.idempotentInvocationHandle(
            Target.virtualObject(
                extractServiceName(AwakeableHolder::class.java), interpreterId, "run"),
            myIdempotencyId,
            TypeTag.of(String::class.java))

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
          assertThat(awakeableHolder.request { getAwakeable() }.call().response).isNotBlank
        }
    awakeableHolder
        .request { resolveAwakeable(response) }
        .options(idempotentCallOptions)
        .call()
        .response

    // Attach should be completed
    assertThat(blockedFut.await().response).isEqualTo(response)

    // Invoke get output
    assertThat(invocationHandle.getOutputSuspend().response().value).isEqualTo(response)
  }

  @Test
  @DisplayName(
      "Make a handler ingress private and try to call it both directly and through a proxy service")
  fun privateService(
      @InjectAdminURI adminURI: URI,
      @InjectClient ingressClient: Client,
  ) = runTest {
    val adminServiceClient = ServiceApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))
    val greeterClient = ingressClient.toService<PrivateGreeter>()

    // Wait for the service to be private
    await withAlias
        "the service is private" untilAsserted
        {
          val ctx = currentCoroutineContext()
          assertThatThrownBy {
                runBlocking(ctx) { greeterClient.request { greet("Francesco") }.call().response }
              }
              .asInstanceOf(InstanceOfAssertFactories.type(IngressException::class.java))
              .returns(400, IngressException::getStatusCode)
        }

    // Send a request through the proxy client
    assertThat(
            ingressClient
                .toService<ProxyGreeter>()
                .request { greet("Francesco") }
                .options(idempotentCallOptions)
                .call()
                .response)
        .isEqualTo("Hello Francesco")

    // Make the service public again
    adminServiceClient.modifyService(
        extractServiceName(PrivateGreeter::class.java), ModifyServiceRequest()._public(true))

    // Wait to get the correct count
    await withAlias
        "the service becomes public again" untilAsserted
        {
          assertThat(greeterClient.request { greet("Francesco") }.call().response)
              .isEqualTo("Hello Francesco")
        }
  }
}
