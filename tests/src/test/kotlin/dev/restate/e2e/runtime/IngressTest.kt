// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.e2e.Containers
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import dev.restate.sdk.client.RequestOptions
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import my.restate.e2e.services.CounterClient
import my.restate.e2e.services.CounterUpdateResponse
import my.restate.e2e.services.HeadersPassThroughTestClient
import my.restate.e2e.services.ProxyCounterClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Disabled
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
                        CounterClient.COMPONENT_NAME,
                        ProxyCounterClient.COMPONENT_NAME,
                        HeadersPassThroughTestClient.COMPONENT_NAME))
                .build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @Disabled
  fun idempotentInvoke(@InjectIngressClient ingressClient: IngressClient) {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()
    val requestOptions =
        RequestOptions().withIdempotency(myIdempotencyId, 3.seconds.toJavaDuration())

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
  fun headersPassThrough(@InjectIngressClient ingressClient: IngressClient) {
    val headerName = "x-my-custom-header"
    val headerValue = "x-my-custom-value"

    assertThat(
            HeadersPassThroughTestClient.fromIngress(ingressClient)
                .echoHeaders(RequestOptions().withHeader(headerName, headerValue)))
        .containsEntry(headerName, headerValue)
  }
}
