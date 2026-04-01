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
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.stateKey
import dev.restate.sdktesting.infra.*
import dev.restate.sdktesting.tests.Kafka.createKafkaSubscription
import dev.restate.sdktesting.tests.Kafka.produceMessagesToKafka
import dev.restate.sdktesting.tests.Kafka.registerKafkaCluster
import java.net.URI
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated

@Tag("only-single-node" /* This test depends on metadata propagation happening immediately */)
@Isolated
class KafkaDynamicSetupTest {

  @VirtualObject
  @Name("Counter")
  class Counter {
    companion object {
      private val COUNTER_KEY: StateKey<Long> = stateKey("counter")
    }

    @Handler
    suspend fun add(ctx: ObjectContext, value: Long): Long {
      val current = ctx.get(COUNTER_KEY) ?: 0L
      val newValue = current + value
      ctx.set(COUNTER_KEY, newValue)
      return newValue
    }

    @Handler
    suspend fun get(ctx: ObjectContext): Long {
      return ctx.get(COUNTER_KEY) ?: 0L
    }
  }

  @Service
  @Name("EventHandler")
  class EventHandler {
    @Serializable data class ProxyRequest(val key: String, val value: Long)

    @Handler
    suspend fun oneWayCall(ctx: Context, request: ProxyRequest) {
      KafkaDynamicSetupTestCounterClient.fromContext(ctx, request.key).send().add(request.value)
    }
  }

  companion object {
    private const val COUNTER_TOPIC = "counter"
    private const val EVENT_HANDLER_TOPIC = "event-handler"

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(Counter()).bind(EventHandler()))
      withContainer("kafka", KafkaContainer(COUNTER_TOPIC, EVENT_HANDLER_TOPIC))
    }

    @JvmStatic
    @BeforeAll
    fun beforeAll(
        @InjectAdminURI adminURI: URI,
    ) {
      registerKafkaCluster(adminURI)
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun handleEventInCounterService(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    val counter = UUID.randomUUID().toString()

    // Register subscription
    createKafkaSubscription(adminURI, COUNTER_TOPIC, "Counter", "add")

    // Produce message to kafka
    produceMessagesToKafka(
        kafkaPort, COUNTER_TOPIC, listOf(counter to "1", counter to "2", counter to "3"))

    await withAlias
        "Updates from Kafka are visible in the counter" untilAsserted
        {
          assertThat(KafkaDynamicSetupTestCounterClient.fromClient(ingressClient, counter).get())
              .isEqualTo(6L)
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun handleEventInEventHandler(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    val counter = UUID.randomUUID().toString()

    // Register subscription
    createKafkaSubscription(adminURI, EVENT_HANDLER_TOPIC, "EventHandler", "oneWayCall")

    // Produce message to kafka
    produceMessagesToKafka(
        kafkaPort,
        EVENT_HANDLER_TOPIC,
        listOf(
            null to Json.encodeToString(EventHandler.ProxyRequest(counter, 1)),
            null to Json.encodeToString(EventHandler.ProxyRequest(counter, 2)),
            null to Json.encodeToString(EventHandler.ProxyRequest(counter, 3))))

    await withAlias
        "Updates from Kafka are visible in the counter" untilAsserted
        {
          assertThat(KafkaDynamicSetupTestCounterClient.fromClient(ingressClient, counter).get())
              .isEqualTo(6L)
        }
  }
}
