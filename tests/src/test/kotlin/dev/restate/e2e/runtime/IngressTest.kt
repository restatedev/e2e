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
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.InjectMetaURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import dev.restate.sdk.client.RequestOptions
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import my.restate.e2e.services.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
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
                        CounterClient.SERVICE_NAME,
                        ProxyCounterClient.SERVICE_NAME,
                        HeadersPassThroughTestClient.SERVICE_NAME))
                .build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @DisplayName("Idempotent invocation to a virtual object")
  fun idempotentInvokeVirtualObject(
      @InjectMetaURL metaURL: URL,
      @InjectIngressClient ingressClient: IngressClient
  ) {
    // Let's update the idempotency retention time to 3 seconds, to make this test faster
    val adminServiceClient = ServiceApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    adminServiceClient.modifyService(
        CounterClient.SERVICE_NAME, ModifyServiceRequest().idempotencyRetention("3s"))

    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions = RequestOptions().withIdempotency(myIdempotencyId)

    val counterClient = CounterClient.fromIngress(ingressClient, counterRandomName)

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
  fun idempotentInvokeService(@InjectIngressClient ingressClient: IngressClient) {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions = RequestOptions().withIdempotency(myIdempotencyId)

    val counterClient = CounterClient.fromIngress(ingressClient, counterRandomName)
    val proxyCounterClient = ProxyCounterClient.fromIngress(ingressClient)

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
  fun idempotentInvokeSend(@InjectIngressClient ingressClient: IngressClient) {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions = RequestOptions().withIdempotency(myIdempotencyId)

    val counterClient = CounterClient.fromIngress(ingressClient, counterRandomName)

    // Send request twice
    counterClient.send().add(2, requestOptions)
    counterClient.send().add(2, requestOptions)

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
  fun headersPassThrough(@InjectIngressClient ingressClient: IngressClient) {
    val headerName = "x-my-custom-header"
    val headerValue = "x-my-custom-value"

    assertThat(
            HeadersPassThroughTestClient.fromIngress(ingressClient)
                .echoHeaders(RequestOptions().withHeader(headerName, headerValue)))
        .containsEntry(headerName, headerValue)
  }
}
