// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.client.Client
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.Context
import dev.restate.sdktesting.infra.ContainerHandle
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectContainerHandle
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class JaegerTracingTest {

  @Serializable data class JaegerResponse(val data: List<JaegerTrace> = emptyList())

  @Serializable data class JaegerTrace(val traceID: String, val spans: List<JaegerSpan>)

  @Serializable
  data class JaegerSpan(
      val traceID: String,
      val spanID: String,
      val operationName: String,
      val references: List<SpanRef> = emptyList(),
      val startTime: Long,
      val duration: Long,
      val tags: List<Tag> = emptyList(),
      val process: Process? = null
  )

  @Serializable data class SpanRef(val refType: String, val traceID: String, val spanID: String)

  @Serializable data class Tag(val key: String, val type: String, val value: JsonElement)

  @Serializable data class Process(val serviceName: String, val tags: List<Tag> = emptyList())

  @Service
  @Name("GreeterService")
  class GreeterService {
    @Handler
    suspend fun greet(ctx: Context, name: String): String {
      // Get the current span from the OpenTelemetry context
      val span = Span.fromContext(ctx.request().otelContext())

      // Verify that this is a server span (meaning it was created from a parent)
      assertThat(span.spanContext.isRemote).isTrue()
      assertThat(span.spanContext.isValid).isTrue()

      return "Hello, $name!"
    }
  }

  companion object {
    private const val JAEGER_HOSTNAME = "jaeger"
    private const val JAEGER_QUERY_PORT = 16686
    private const val JAEGER_COLLECTOR_PORT = 4317 // OTLP gRPC port
    private val json = Json {
      ignoreUnknownKeys = true
      coerceInputValues = true
    }

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      // Create Jaeger container
      val jaeger =
          GenericContainer("jaegertracing/all-in-one:latest")
              .withExposedPorts(JAEGER_QUERY_PORT, JAEGER_COLLECTOR_PORT)
              .waitingFor(Wait.forHttp("/").forPort(JAEGER_QUERY_PORT))

      // Add Jaeger container
      withContainer(JAEGER_HOSTNAME, jaeger)

      // Configure Restate to send traces to Jaeger
      withConfig(
          RestateConfigSchema().apply {
            tracingEndpoint = "http://$JAEGER_HOSTNAME:$JAEGER_COLLECTOR_PORT"
            tracingFilter = "info"
          })

      withEndpoint(
          Endpoint
              // Add our greeter service
              .bind(GreeterService())
              // Make sure local otel propagates w3c trace context
              .withOpenTelemetry(
                  OpenTelemetry.propagating(
                      ContextPropagators.create(W3CTraceContextPropagator.getInstance()))))
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun shouldGenerateTraces(
      @InjectClient client: Client,
      @InjectContainerHandle(JAEGER_HOSTNAME) jaeger: ContainerHandle
  ) = runTest {
    // Call the greeter service
    val greeter = JaegerTracingTestGreeterServiceClient.fromClient(client)
    val response = greeter.greet("Alice", idempotentCallOptions)
    assertThat(response).isEqualTo("Hello, Alice!")

    val jaegerPort = jaeger.getMappedPort(JAEGER_QUERY_PORT)
    val httpClient = HttpClient.newHttpClient()

    await withAlias
        "traces are available" untilAsserted
        {
          val request =
              HttpRequest.newBuilder()
                  .uri(URI.create("http://localhost:$jaegerPort/api/traces?service=GreeterService"))
                  .GET()
                  .build()

          val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
          assertThat(response.statusCode()).isEqualTo(200)

          val traces = json.decodeFromString<JaegerResponse>(response.body()).data

          assertThat(traces).isNotEmpty()
          val trace = traces.first()

          // Verify spans
          assertThat(trace.spans).anySatisfy { span ->
            assertThat(span.operationName).contains("GreeterService/greet")
            assertThat(span.tags).anySatisfy { tag ->
              assertThat(tag.key).isEqualTo("restate.invocation.id")
            }
            assertThat(span.tags).anySatisfy { tag ->
              assertThat(tag.key).isEqualTo("restate.invocation.target")
              assertThat(tag.value).isEqualTo(JsonPrimitive("GreeterService/greet"))
            }
          }
        }
  }
}
