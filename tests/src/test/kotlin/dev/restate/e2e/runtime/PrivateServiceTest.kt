// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.rpc.Code
import dev.restate.e2e.Containers
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.CounterProto.GetResponse
import dev.restate.e2e.services.counter.ProxyCounterGrpc.ProxyCounterBlockingStub
import dev.restate.e2e.services.counter.counterAddRequest
import dev.restate.e2e.services.counter.counterRequest
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.meta.client.ServicesClient
import dev.restate.e2e.utils.meta.models.ModifyServiceRequest
import io.grpc.StatusRuntimeException
import java.net.URL
import java.util.UUID
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.InstanceOfAssertFactories.type
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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_COUNTER_SERVICE_SPEC)
                .build())
  }

  @Test
  fun privateService(
      @InjectMetaURL metaURL: URL,
      @InjectBlockingStub counterClient: CounterBlockingStub,
      @InjectBlockingStub proxyCounterClient: ProxyCounterBlockingStub
  ) {
    val client = ServicesClient(ObjectMapper(), metaURL.toString(), OkHttpClient())
    val counterId = UUID.randomUUID().toString()

    counterClient.add(
        counterAddRequest {
          counterName = counterId
          value = 1
        })

    // Make the service private
    assertThat(
            client
                .modifyService(ModifyServiceRequest(public = false), CounterGrpc.SERVICE_NAME)
                .statusCode)
        .isGreaterThanOrEqualTo(200)
        .isLessThan(300)

    // Wait for the service to be private
    await untilAsserted
        {
          assertThatThrownBy { counterClient.get(counterRequest { counterName = counterId }) }
              .asInstanceOf(type(StatusRuntimeException::class.java))
              .returns(Code.PERMISSION_DENIED_VALUE) { it.status.code.value() }
        }

    // Send a request through the proxy client
    proxyCounterClient.addInBackground(
        counterAddRequest {
          counterName = counterId
          value = 1
        })

    // Make the service public again
    assertThat(
            client
                .modifyService(ModifyServiceRequest(public = true), CounterGrpc.SERVICE_NAME)
                .statusCode)
        .isGreaterThanOrEqualTo(200)
        .isLessThan(300)

    // Wait to get the correct count
    await untilAsserted
        {
          assertThat(counterClient.get(counterRequest { counterName = counterId }))
              .returns(2L, GetResponse::getValue)
        }
  }
}
