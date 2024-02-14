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
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.CounterProto.*
import dev.restate.e2e.services.counter.counterAddRequest
import dev.restate.e2e.services.counter.counterRequest
import dev.restate.e2e.utils.*
import dev.restate.generated.IngressGrpc.IngressBlockingStub
import dev.restate.generated.invokeRequest
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.util.*
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
                JsonUtils.jacksonBodyPublisher(
                    mapOf(
                        "service" to CounterGrpc.SERVICE_NAME,
                        "method" to CounterGrpc.getAddMethod().bareMethodName,
                        "argument" to mapOf("counterName" to counterName, "value" to 2))))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, JsonUtils.jacksonBodyHandler())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.headers().firstValue("content-type"))
        .get()
        .asString()
        .contains("application/json")
    assertThat(response.body().get("id").asText()).isNotEmpty()

    await untilCallTo
        {
          counterClient.get(CounterRequest.newBuilder().setCounterName(counterName).build())
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
              pb =
                  counterAddRequest {
                        counterName = counterRandomName
                        value = 2
                      }
                      .toByteString()
            })

    assertThat(response.id).isNotEmpty()

    await untilCallTo
        {
          counterClient.get(CounterRequest.newBuilder().setCounterName(counterRandomName).build())
        } matches
        { num ->
          num!!.value == 2L
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  fun idempotentInvoke(@InjectBlockingStub counterClient: CounterBlockingStub) {
    val counterRandomName = UUID.randomUUID().toString()
    val myIdempotencyId = UUID.randomUUID().toString()

    val requestMetadata = Metadata()
    requestMetadata.put(
        Metadata.Key.of("idempotency-key", Metadata.ASCII_STRING_MARSHALLER), myIdempotencyId)
    requestMetadata.put(
        Metadata.Key.of("idempotency-retention-period", Metadata.ASCII_STRING_MARSHALLER), "3")

    val idempotentCounterClient =
        counterClient.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(requestMetadata))

    val counterAdd = counterAddRequest {
      counterName = counterRandomName
      value = 2
    }

    // First call updates the value
    val firstResponse = idempotentCounterClient.getAndAdd(counterAdd)
    assertThat(firstResponse)
        .returns(0, CounterUpdateResult::getOldValue)
        .returns(2, CounterUpdateResult::getNewValue)

    // Next call returns the same value
    val secondResponse = idempotentCounterClient.getAndAdd(counterAdd)
    assertThat(secondResponse)
        .returns(0L, CounterUpdateResult::getOldValue)
        .returns(2L, CounterUpdateResult::getNewValue)

    // Await until the idempotency id is cleaned up and the next idempotency call updates the
    // counter again
    await untilAsserted
        {
          assertThat(idempotentCounterClient.getAndAdd(counterAdd))
              .returns(2, CounterUpdateResult::getOldValue)
              .returns(4, CounterUpdateResult::getNewValue)
        }

    // State in the counter service is now equal to 4
    assertThat(counterClient.get(counterRequest { counterName = counterRandomName }))
        .returns(4L, GetResponse::getValue)
  }
}
