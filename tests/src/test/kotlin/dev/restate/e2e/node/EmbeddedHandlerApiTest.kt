package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_CONTAINER_SPEC
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_HOSTNAME
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_PORT
import dev.restate.e2e.Containers.HANDLER_API_COUNTER_SERVICE_NAME
import dev.restate.e2e.Containers.nodeServicesContainer
import dev.restate.e2e.Utils.jacksonBodyHandler
import dev.restate.e2e.Utils.jacksonBodyPublisher
import dev.restate.e2e.utils.*
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
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

    val client = HttpClient.newHttpClient()

    for (i in 0..2) {
      val req =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://localhost:${embeddedHandlerServerPort}/increment_counter_test"))
              .headers("Content-Type", "application/json")
              .POST(jacksonBodyPublisher(mapOf("id" to operationUuid, "input" to counterUuid)))
              .build()
      val response = client.send(req, jacksonBodyHandler())
      assertThat(response.statusCode()).isEqualTo(200)
      // We increment the counter only once
      assertThat(response.body().get("result").asInt()).isEqualTo(1)
    }

    // Check the counter
    val req =
        HttpRequest.newBuilder(
                URI.create("${httpEndpointURL}$HANDLER_API_COUNTER_SERVICE_NAME/get"))
            .headers("Content-Type", "application/json")
            .POST(jacksonBodyPublisher(mapOf("key" to counterUuid)))
            .build()

    val response = client.send(req, jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body().get("response").get("counter").asInt()).isEqualTo(1)
  }
}
