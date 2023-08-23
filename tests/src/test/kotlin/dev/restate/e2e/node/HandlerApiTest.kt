package dev.restate.e2e.node

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.e2e.Containers
import dev.restate.e2e.Utils.jacksonBodyHandler
import dev.restate.e2e.Utils.jacksonBodyPublisher
import dev.restate.e2e.utils.InjectGrpcIngressURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest

/** Test the Handler API */
class HandlerApiTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_HANDLER_API_ECHO_TEST_SERVICE_SPEC)
                .build())

    private val objMapper = ObjectMapper()
  }

  // This test reproduces https://github.com/restatedev/restate/issues/687
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun echo(
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    val client = HttpClient.newHttpClient()

    val expectedOutput: JsonNode = objMapper.readTree(
      """{ "a": false, "b": 0.0, "c": "", "d": true, "e": 1.0, "f": "hello"}"""
    )

    val req =
        HttpRequest.newBuilder(URI.create("${httpEndpointURL}${Containers.HANDLER_API_ECHO_TEST_SERVICE_NAME}/EchoEcho"))
            .POST(jacksonBodyPublisher(mapOf("request" to expectedOutput)))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.headers().firstValue("content-type"))
        .get()
        .asString()
        .contains("application/json")
    assertThat(response.body().get("response")).isEqualTo(expectedOutput)
  }
}
