// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_CONTAINER_SPEC
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_HOSTNAME
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_PORT
import dev.restate.e2e.Containers.HANDLER_API_COUNTER_SERVICE_NAME
import dev.restate.e2e.Containers.nodeServicesContainer
import dev.restate.e2e.Utils.postJsonRequest
import dev.restate.e2e.utils.*
import java.net.URL
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/** Test the Embedded handler API */
class EmbeddedHandlerApiTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withContainer(EMBEDDED_HANDLER_SERVER_CONTAINER_SPEC)
                .withContainer(Containers.INT_SORTER_HTTP_SERVER_CONTAINER_SPEC)
                .withServiceEndpoint(
                    nodeServicesContainer("handler-api-counter", HANDLER_API_COUNTER_SERVICE_NAME))
                .build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun incrementCounter(
      @InjectContainerPort(
          hostName = EMBEDDED_HANDLER_SERVER_HOSTNAME, port = EMBEDDED_HANDLER_SERVER_PORT)
      embeddedHandlerServerPort: Int,
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    val counterUuid = UUID.randomUUID().toString()
    val operationUuid = UUID.randomUUID().toString()

    for (i in 0..2) {
      val response =
          postJsonRequest(
              "http://localhost:${embeddedHandlerServerPort}/increment_counter_test",
              mapOf("id" to operationUuid, "input" to counterUuid))
      assertThat(response.statusCode()).isEqualTo(200)
      // We increment the counter only once
      assertThat(response.body().get("result").asInt()).isEqualTo(1)
    }

    val response =
        postJsonRequest(
            "${httpEndpointURL}$HANDLER_API_COUNTER_SERVICE_NAME/get", mapOf("key" to counterUuid))
    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body().get("response").get("counter").asInt()).isEqualTo(1)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun oneWayIncrementCounter(
      @InjectContainerPort(
          hostName = EMBEDDED_HANDLER_SERVER_HOSTNAME, port = EMBEDDED_HANDLER_SERVER_PORT)
      embeddedHandlerServerPort: Int,
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    runAsyncIncrementCounterTest(
        "one_way_increment_counter_test", embeddedHandlerServerPort, httpEndpointURL)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun delayedIncrementCounter(
      @InjectContainerPort(
          hostName = EMBEDDED_HANDLER_SERVER_HOSTNAME, port = EMBEDDED_HANDLER_SERVER_PORT)
      embeddedHandlerServerPort: Int,
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    runAsyncIncrementCounterTest(
        "delayed_increment_counter_test", embeddedHandlerServerPort, httpEndpointURL)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectAndAwakeable(
      @InjectContainerPort(
          hostName = EMBEDDED_HANDLER_SERVER_HOSTNAME, port = EMBEDDED_HANDLER_SERVER_PORT)
      embeddedHandlerServerPort: Int,
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    val operationUuid = UUID.randomUUID().toString()

    val response =
        postJsonRequest(
            "http://localhost:${embeddedHandlerServerPort}/side_effect_and_awakeable",
            mapOf("id" to operationUuid, "itemsNumber" to 10))
    assertThat(response.statusCode()).isEqualTo(200)

    // Check numbers are sorted
    val numbersNode = response.body().get("numbers")
    assertThat(numbersNode.isArray).isTrue()
    assertThat(List(numbersNode.size(), numbersNode::get).map { it.asInt() }).isSorted.hasSize(10)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun consecutiveSideEffects(
      @InjectContainerPort(
          hostName = EMBEDDED_HANDLER_SERVER_HOSTNAME, port = EMBEDDED_HANDLER_SERVER_PORT)
      embeddedHandlerServerPort: Int,
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    val operationUuid = UUID.randomUUID().toString()

    val response =
        postJsonRequest(
            "http://localhost:${embeddedHandlerServerPort}/consecutive_side_effects",
            mapOf("id" to operationUuid))
    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body().get("invocationCount").asInt()).isEqualTo(3)
  }

  private fun runAsyncIncrementCounterTest(
      path: String,
      embeddedHandlerServerPort: Int,
      httpEndpointURL: URL
  ) {
    val counterUuid = UUID.randomUUID().toString()
    val operationUuid = UUID.randomUUID().toString()

    for (i in 0..2) {
      val response =
          postJsonRequest(
              "http://localhost:${embeddedHandlerServerPort}/${path}",
              mapOf("id" to operationUuid, "input" to counterUuid))
      assertThat(response.statusCode()).isEqualTo(200)
    }

    await untilAsserted
        {
          val response =
              postJsonRequest(
                  "${httpEndpointURL}$HANDLER_API_COUNTER_SERVICE_NAME/get",
                  mapOf("key" to counterUuid))
          assertThat(response.statusCode()).isEqualTo(200)
          assertThat(response.body().get("response").get("counter").asInt()).isEqualTo(1)
        }
  }
}
