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
import dev.restate.sdk.client.Client
import dev.restate.sdk.client.IngressException
import java.net.URL
import java.util.*
import my.restate.e2e.services.CounterClient
import my.restate.e2e.services.CounterDefinitions
import my.restate.e2e.services.ProxyCounter
import my.restate.e2e.services.ProxyCounterClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.InstanceOfAssertFactories
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Test supporting private services */
class PrivateServiceTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .build())
  }

  @Test
  fun privateService(
      @InjectMetaURL metaURL: URL,
      @InjectClient ingressClient: Client,
  ) {
    val adminServiceClient = ServiceApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    val counterId = UUID.randomUUID().toString()
    val counterClient = CounterClient.fromClient(ingressClient, counterId)

    counterClient.add(1)

    // Make the service private
    adminServiceClient.modifyService(
        CounterDefinitions.SERVICE_NAME, ModifyServiceRequest()._public(false))

    // Wait for the service to be private
    await untilAsserted
        {
          assertThatThrownBy { counterClient.get() }
              .asInstanceOf(InstanceOfAssertFactories.type(IngressException::class.java))
              .returns(400, IngressException::getStatusCode)
        }

    // Send a request through the proxy client
    ProxyCounterClient.fromClient(ingressClient)
        .addInBackground(ProxyCounter.AddRequest(counterId, 1))

    // Make the service public again
    adminServiceClient.modifyService(
        CounterDefinitions.SERVICE_NAME, ModifyServiceRequest()._public(true))

    // Wait to get the correct count
    await untilAsserted { assertThat(counterClient.get()).isEqualTo(2L) }
  }
}
