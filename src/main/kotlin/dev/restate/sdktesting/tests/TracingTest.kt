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
import dev.restate.sdk.kotlin.*
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectContainerPort
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import dev.restate.sdktesting.tests.Tracing.JAEGER_HOSTNAME
import dev.restate.sdktesting.tests.Tracing.JAEGER_QUERY_PORT
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TracingTest {

  @Service
  @Name("GreeterService")
  class GreeterService {
    @Handler
    suspend fun greet(ctx: Context, name: String): String {
      // Get the current span from the OpenTelemetry context
      val span = io.opentelemetry.api.trace.Span.fromContext(ctx.request().openTelemetryContext)

      // Verify that this is a server span (meaning it was created from a parent)
      assertThat(span.spanContext.isRemote).isTrue()
      assertThat(span.spanContext.isValid).isTrue()

      return "Hello, $name!"
    }
  }

  companion object {

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      // Add Jaeger container
      withContainer(Tracing.jaegerContainer())

      // Configure Restate to send traces to Jaeger
      withConfig(RestateConfigSchema().apply(Tracing.configSchema))

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
      @InjectContainerPort(hostName = JAEGER_HOSTNAME, port = JAEGER_QUERY_PORT) jaegerPort: Int,
  ) = runTest {
    // Call the greeter service
    val greeter = TracingTestGreeterServiceClient.fromClient(client)
    val response = greeter.greet("Alice", idempotentCallOptions)
    assertThat(response).isEqualTo("Hello, Alice!")

    await withAlias
        "traces are available" untilAsserted
        {
          val traces = Tracing.getTraces(jaegerPort, "GreeterService")

          assertThat(traces.result.resourceSpans).isNotEmpty()

          // Find the GreeterService spans
          val greeterSpans =
              traces.result.resourceSpans
                  .flatMap { it.scopeSpans }
                  .flatMap { it.spans }
                  .filter { it.name.contains("GreeterService/greet") }

          assertThat(greeterSpans).isNotEmpty()

          // Verify span attributes
          val span = greeterSpans.first()

          // Verify Restate-specific attributes
          val attributes = span.attributes.associate { it.key to it.value.stringValue }
          assertThat(attributes).containsKey("restate.invocation.id")
          assertThat(attributes).containsEntry("restate.invocation.target", "GreeterService/greet")
        }
  }
}
