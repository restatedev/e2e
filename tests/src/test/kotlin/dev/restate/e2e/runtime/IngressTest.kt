// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.admin.api.ServiceApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.ModifyServiceRequest
import dev.restate.e2e.Containers
import dev.restate.e2e.utils.InjectClient
import dev.restate.e2e.utils.InjectMetaURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.JsonSerdes
import dev.restate.sdk.client.CallRequestOptions
import dev.restate.sdk.client.Client
import dev.restate.sdk.client.SendResponse.SendStatus
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import my.restate.e2e.services.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

class IngressTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-counter",
                        CounterDefinitions.SERVICE_NAME,
                        ProxyCounterDefinitions.SERVICE_NAME,
                        HeadersPassThroughTestDefinitions.SERVICE_NAME,
                        AwakeableHolderDefinitions.SERVICE_NAME,
                        EchoDefinitions.SERVICE_NAME))
                .build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @DisplayName("Idempotent invocation to a virtual object")
  fun idempotentInvokeVirtualObject(
      @InjectMetaURL metaURL: URL,
      @InjectClient ingressClient: Client
  ) {
    // Let's update the idempotency retention time to 3 seconds, to make this test faster
    val adminServiceClient = ServiceApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    adminServiceClient.modifyService(
        CounterDefinitions.SERVICE_NAME, ModifyServiceRequest().idempotencyRetention("3s"))

    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions = CallRequestOptions().withIdempotency(myIdempotencyId)

    val counterClient = CounterClient.fromClient(ingressClient, counterRandomName)

    // First call updates the value
    val firstResponse = counterClient.getAndAdd(2, requestOptions)
    assertThat(firstResponse)
        .returns(0, CounterUpdateResponse::getOldValue)
        .returns(2, CounterUpdateResponse::getNewValue)

    // Next call returns the same value
    val secondResponse = counterClient.getAndAdd(2, requestOptions)
    assertThat(secondResponse)
        .returns(0L, CounterUpdateResponse::getOldValue)
        .returns(2L, CounterUpdateResponse::getNewValue)

    // Await until the idempotency id is cleaned up and the next idempotency call updates the
    // counter again
    await untilAsserted
        {
          assertThat(counterClient.getAndAdd(2, requestOptions))
              .returns(2, CounterUpdateResponse::getOldValue)
              .returns(4, CounterUpdateResponse::getNewValue)
        }

    // State in the counter service is now equal to 4
    assertThat(counterClient.get()).isEqualTo(4L)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @DisplayName("Idempotent invocation to a service")
  fun idempotentInvokeService(@InjectClient ingressClient: Client) {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions = CallRequestOptions().withIdempotency(myIdempotencyId)

    val counterClient = CounterClient.fromClient(ingressClient, counterRandomName)
    val proxyCounterClient = ProxyCounterClient.fromClient(ingressClient)

    // Send request twice
    proxyCounterClient.addInBackground(
        ProxyCounter.AddRequest(counterRandomName, 2), requestOptions)
    proxyCounterClient.addInBackground(
        ProxyCounter.AddRequest(counterRandomName, 2), requestOptions)

    // Wait for get
    await untilAsserted { assertThat(counterClient.get()).isEqualTo(2) }

    // Without request options this should be executed immediately and return 4
    assertThat(counterClient.getAndAdd(2))
        .returns(2, CounterUpdateResponse::getOldValue)
        .returns(4, CounterUpdateResponse::getNewValue)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @DisplayName("Idempotent invocation to a virtual object using send")
  fun idempotentInvokeSend(@InjectClient ingressClient: Client) {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions = CallRequestOptions().withIdempotency(myIdempotencyId)

    val counterClient = CounterClient.fromClient(ingressClient, counterRandomName)

    // Send request twice
    val firstInvocationSendStatus = counterClient.send().add(2, requestOptions)
    assertThat(firstInvocationSendStatus.status).isEqualTo(SendStatus.ACCEPTED)
    val secondInvocationSendStatus = counterClient.send().add(2, requestOptions)
    assertThat(secondInvocationSendStatus.status).isEqualTo(SendStatus.PREVIOUSLY_ACCEPTED)

    // IDs should be the same
    assertThat(firstInvocationSendStatus.invocationId)
        .startsWith("inv")
        .isEqualTo(secondInvocationSendStatus.invocationId)

    // Wait for get
    await untilAsserted { assertThat(counterClient.get()).isEqualTo(2) }

    // Without request options this should be executed immediately and return 4
    assertThat(counterClient.getAndAdd(2))
        .returns(2, CounterUpdateResponse::getOldValue)
        .returns(4, CounterUpdateResponse::getNewValue)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @DisplayName("Idempotent send then attach/getOutput")
  fun idempotentSendThenAttach(@InjectClient ingressClient: Client) {
    val awakeableKey = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val response = "response"

    // Send request
    val echoClient = EchoClient.fromClient(ingressClient)
    val invocationId =
        echoClient
            .send()
            .blockThenEcho(awakeableKey, CallRequestOptions().withIdempotency(myIdempotencyId))
            .invocationId
    val invocationHandle = ingressClient.invocationHandle(invocationId, JsonSerdes.STRING)

    // Attach to request
    val blockedFut = invocationHandle.attachAsync()

    // Get output throws exception
    assertThat(invocationHandle.output.isReady).isFalse()

    // Blocked fut should still be blocked
    assertThat(blockedFut).isNotDone

    // Unblock
    val awakeableHolderClient = AwakeableHolderClient.fromClient(ingressClient, awakeableKey)
    await until { awakeableHolderClient.hasAwakeable() }
    awakeableHolderClient.unlock(response)

    // Attach should be completed
    assertThat(blockedFut.get()).isEqualTo(response)

    // Invoke get output
    assertThat(invocationHandle.output.value).isEqualTo(response)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  fun headersPassThrough(@InjectClient ingressClient: Client) {
    val headerName = "x-my-custom-header"
    val headerValue = "x-my-custom-value"

    assertThat(
            HeadersPassThroughTestClient.fromClient(ingressClient)
                .echoHeaders(CallRequestOptions().withHeader(headerName, headerValue)))
        .containsEntry(headerName, headerValue)
  }
}
