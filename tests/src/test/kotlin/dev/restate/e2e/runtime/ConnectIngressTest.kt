package dev.restate.e2e.runtime

import com.fasterxml.jackson.databind.node.JsonNodeType
import dev.restate.e2e.Containers
import dev.restate.e2e.Utils.jacksonBodyHandler
import dev.restate.e2e.Utils.jacksonBodyPublisher
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.utils.InjectGrpcIngressURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/** Test the Connect ingress support */
class ConnectIngressTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .build())

    val client = HttpClient.newHttpClient()
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun getAndAdd(@InjectGrpcIngressURL httpEndpointURL: URL) {
    val req =
        HttpRequest.newBuilder(
                URI.create("$httpEndpointURL${CounterGrpc.getGetAndAddMethod().fullMethodName}"))
            .POST(jacksonBodyPublisher(mapOf("counterName" to "my-counter", "value" to 1)))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.headers().firstValue("content-type"))
        .get()
        .asString()
        .contains("application/json")

    assertThat(response.body().has("newValue"))
        .withFailMessage {
          "Expecting newValue field in body. Body is " + response.body().toPrettyString()
        }
        .isTrue()
    val newValueField = response.body().get("newValue")
    // This is because the default for serialize_stringify_64_bit_integers is true
    assertThat(newValueField.nodeType)
        .withFailMessage {
          "Expecting newValue field to be STRING. Body is " + response.body().toPrettyString()
        }
        .isEqualTo(JsonNodeType.STRING)
    // asLong parses the string!
    assertThat(newValueField.asLong())
        .withFailMessage {
          "Expecting newValue field == 1L. Body is " + response.body().toPrettyString()
        }
        .isEqualTo(1L)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun badContentType(@InjectGrpcIngressURL httpEndpointURL: URL) {
    val req =
        HttpRequest.newBuilder(
                URI.create("$httpEndpointURL${CounterGrpc.getGetAndAddMethod().fullMethodName}"))
            .POST(jacksonBodyPublisher(mapOf("counterName" to "my-counter", "value" to 1)))
            .headers("Content-Type", "application/whatever")
            .build()

    val response = client.send(req, jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(415)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun malformedJson(@InjectGrpcIngressURL httpEndpointURL: URL) {
    val req =
        HttpRequest.newBuilder(
                URI.create("$httpEndpointURL${CounterGrpc.getGetAndAddMethod().fullMethodName}"))
            .POST(BodyPublishers.ofString("{"))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(400)
  }
}
