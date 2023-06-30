package dev.restate.e2e.runtime

import dev.restate.e2e.Containers
import dev.restate.e2e.Utils.jacksonBodyHandler
import dev.restate.e2e.Utils.jacksonBodyPublisher
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.CounterProto
import dev.restate.e2e.services.counter.counterAddRequest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.InjectGrpcIngressURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.generated.IngressGrpc.IngressBlockingStub
import dev.restate.generated.invokeRequest
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/** Test the Connect ingress support */
class IngressServiceTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun invokeAddThroughConnect(
      @InjectGrpcIngressURL httpEndpointURL: URL,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    val counterName = UUID.randomUUID().toString()

    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(URI.create("${httpEndpointURL}dev.restate.Ingress/Invoke"))
            .POST(
                jacksonBodyPublisher(
                    mapOf(
                        "service" to CounterGrpc.SERVICE_NAME,
                        "method" to CounterGrpc.getAddMethod().bareMethodName,
                        "argument" to mapOf("counterName" to counterName, "value" to 2))))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.headers().firstValue("content-type"))
        .get()
        .asString()
        .contains("application/json")
    assertThat(response.body().get("sid").asText()).contains(CounterGrpc.SERVICE_NAME)

    await untilCallTo
        {
          counterClient.get(
              CounterProto.CounterRequest.newBuilder().setCounterName(counterName).build())
        } matches
        { num ->
          num!!.value == 2L
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun invokeAddThroughGrpc(
      @InjectBlockingStub ingressClient: IngressBlockingStub,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    val counterRandomName = UUID.randomUUID().toString()

    val response =
        ingressClient.invoke(
            invokeRequest {
              service = CounterGrpc.SERVICE_NAME
              method = CounterGrpc.getAddMethod().bareMethodName.toString()
              argument =
                  counterAddRequest {
                        counterName = counterRandomName
                        value = 2
                      }
                      .toByteString()
            })

    assertThat(response.sid).contains(CounterGrpc.SERVICE_NAME)

    await untilCallTo
        {
          counterClient.get(
              CounterProto.CounterRequest.newBuilder().setCounterName(counterRandomName).build())
        } matches
        { num ->
          num!!.value == 2L
        }
  }
}
