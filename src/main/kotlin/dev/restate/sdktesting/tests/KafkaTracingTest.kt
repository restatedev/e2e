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
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.SharedObjectContext
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.set
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectContainerPort
import dev.restate.sdktesting.infra.KafkaContainer
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import dev.restate.sdktesting.tests.Tracing.JAEGER_HOSTNAME
import dev.restate.sdktesting.tests.Tracing.JAEGER_QUERY_PORT
import java.net.URI
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KafkaTracingTest {

  @VirtualObject
  @Name("Counter")
  class Counter {
    @Handler
    suspend fun set(ctx: ObjectContext, value: String) {
      check(ctx.get<String>("state") == null)
      ctx.set("state", value)
    }

    @Shared suspend fun get(ctx: SharedObjectContext) = ctx.get<String>("state")
  }

  companion object {
    private const val TOPIC = "my-topic"

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      // Add Jaeger and Kafka container
      withContainer(Tracing.jaegerContainer())
      withContainer("kafka", KafkaContainer(TOPIC))

      // Configure Restate to send traces to Jaeger
      withConfig(RestateConfigSchema().apply(Tracing.configSchema).apply(Kafka.configSchema))

      withEndpoint(Endpoint.bind(Counter()))
    }
  }

  @Test
  fun shouldGenerateTraces(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = JAEGER_HOSTNAME, port = JAEGER_QUERY_PORT) jaegerPort: Int,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
  ) = runTest {
    Kafka.createKafkaSubscription(
        adminURI, TOPIC, KafkaTracingTestCounterHandlers.Metadata.SERVICE_NAME, "set")

    // Produce message to kafka
    Kafka.produceMessagesToKafka(kafkaPort, TOPIC, listOf("a" to Json.encodeToString("a")))

    // Await that state is updated
    val client = KafkaTracingTestCounterClient.fromClient(ingressClient, "a")
    await withAlias "state is updated" untilAsserted { assertThat(client.get()).isEqualTo("a") }

    // Check the traces
    await withAlias
        "traces are available" untilAsserted
        {
          val traces =
              Tracing.getTraces(jaegerPort, KafkaTracingTestCounterHandlers.Metadata.SERVICE_NAME)

          assertThat(traces.result.resourceSpans).isNotEmpty()

          // Find the GreeterService spans
          val counterAddSpans =
              traces.result.resourceSpans
                  .flatMap { it.scopeSpans }
                  .flatMap { it.spans }
                  .filter { it.name.contains("ingress_kafka Counter/{key}/set") }

          assertThat(counterAddSpans).isNotEmpty()

          // Verify span attributes
          val span = counterAddSpans.first()

          // Verify Restate-specific attributes
          val attributes = span.attributes.associate { it.key to it.value.stringValue }
          assertThat(attributes)
              .containsEntry("restate.invocation.target", "Counter/a/set")
              .containsEntry("messaging.system", "kafka")
              .containsEntry("messaging.source.name", TOPIC)
              .containsEntry("messaging.operation.type", "process")
              .containsKeys(
                  "restate.invocation.id",
                  "messaging.consumer.group.name",
                  "messaging.kafka.offset",
                  "messaging.source.partition.id")
        }
  }
}
