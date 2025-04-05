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
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

// OTLP JSON Query API data model
@Serializable data class OTLPResponse(val resourceSpans: List<ResourceSpans> = emptyList())

@Serializable
data class ResourceSpans(val resource: Resource, val scopeSpans: List<ScopeSpans> = emptyList())

@Serializable data class Resource(val attributes: List<KeyValue> = emptyList())

@Serializable
data class ScopeSpans(val scope: InstrumentationScope, val spans: List<Span> = emptyList())

@Serializable data class InstrumentationScope(val name: String, val version: String? = null)

@Serializable
data class Span(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String? = null,
    val name: String,
    val kind: Int,
    val startTimeUnixNano: Long,
    val endTimeUnixNano: Long,
    val attributes: List<KeyValue> = emptyList(),
    val status: Status? = null
)

@Serializable data class KeyValue(val key: String, val value: Value)

@Serializable
data class Value(
    val stringValue: String? = null,
    val intValue: Long? = null,
    val doubleValue: Double? = null,
    val boolValue: Boolean? = null
)

@Serializable data class Status(val code: Int, val message: String? = null)

class JaegerTracingTest {

  @Service
  @Name("GreeterService")
  class GreeterService {
    @Handler
    suspend fun greet(ctx: Context, name: String): String {
      // Get the current span from the OpenTelemetry context
      val span = io.opentelemetry.api.trace.Span.fromContext(ctx.request().otelContext())

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
      // Create Jaeger 2 container
      val jaeger =
          GenericContainer("jaegertracing/jaeger:2.4.0")
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

  @Test
  fun shouldGenerateTraces(
      @InjectClient client: Client,
      @InjectContainerHandle(JAEGER_HOSTNAME) jaeger: ContainerHandle
  ) = runTest {
    // Call the greeter service
    val greeter = JaegerTracingTestGreeterServiceClient.fromClient(client)
    val response = greeter.greet("Alice", idempotentCallOptions)
    assertThat(response).isEqualTo("Hello, Alice!")

    // Query Jaeger for traces
    val jaegerPort = jaeger.getMappedPort(JAEGER_QUERY_PORT)
    val httpClient = HttpClient.newHttpClient()

    await withAlias
        "traces are available" untilAsserted
        {
          val request =
              HttpRequest.newBuilder()
                  .uri(
                      URI.create(
                          "http://localhost:$jaegerPort/api/v3/traces?service.name=GreeterService"))
                  .header("Accept", "application/json")
                  .GET()
                  .build()

          val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
          assertThat(response.statusCode()).isEqualTo(200)

          val traces = json.decodeFromString<OTLPResponse>(response.body())

          assertThat(traces.resourceSpans).isNotEmpty()

          // Find the GreeterService spans
          val greeterSpans =
              traces.resourceSpans
                  .flatMap { it.scopeSpans }
                  .flatMap { it.spans }
                  .filter { it.name.contains("GreeterService/greet") }

          assertThat(greeterSpans).isNotEmpty()

          // Verify span attributes
          val span = greeterSpans.first()
          assertThat(span.kind).isEqualTo(2) // SERVER kind

          // Verify Restate-specific attributes
          val attributes = span.attributes.associate { it.key to it.value.stringValue }
          assertThat(attributes).containsKey("restate.invocation.id")
          assertThat(attributes).containsEntry("restate.invocation.target", "GreeterService/greet")
        }
  }
}
