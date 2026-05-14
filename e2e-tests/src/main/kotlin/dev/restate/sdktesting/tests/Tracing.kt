// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

object Tracing {
  @Serializable data class OTLPResponse(val result: OTLPResult)

  // OTLP JSON Query API data model
  @Serializable data class OTLPResult(val resourceSpans: List<ResourceSpans> = emptyList())

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

  @Serializable data class Status(val code: Int = 599, val message: String? = null)

  private val client = HttpClient.newHttpClient()

  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  const val JAEGER_HOSTNAME = "jaeger"
  const val JAEGER_QUERY_PORT = 16686
  const val JAEGER_COLLECTOR_PORT = 4317 // OTLP gRPC port

  fun getTraces(otlpPort: Int, serviceName: String): OTLPResponse {
    val startTimeEpoch = Instant.EPOCH
    val nowEpoch = Instant.now().plus(24.hours.toJavaDuration())
    val request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:$otlpPort/api/v3/traces?query.service_name=$serviceName&query.start_time_min=$startTimeEpoch&query.start_time_max=$nowEpoch"))
            .header("Accept", "application/json")
            .GET()
            .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(200)

    return json.decodeFromString<OTLPResponse>(response.body())
  }

  fun jaegerContainer() =
      JAEGER_HOSTNAME to
          GenericContainer("jaegertracing/jaeger:2.5.0")
              .withExposedPorts(JAEGER_QUERY_PORT, JAEGER_COLLECTOR_PORT)
              .waitingFor(Wait.forHttp("/").forPort(JAEGER_QUERY_PORT))

  val configSchema: RestateConfigSchema.() -> Unit = {
    tracingEndpoint = "http://$JAEGER_HOSTNAME:$JAEGER_COLLECTOR_PORT"
    tracingFilter = "info"
  }
}
