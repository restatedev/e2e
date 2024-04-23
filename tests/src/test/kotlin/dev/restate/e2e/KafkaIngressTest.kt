// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.admin.api.SubscriptionApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.CreateSubscriptionRequest
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.config.*
import dev.restate.sdk.client.IngressClient
import java.net.URL
import java.util.*
import my.restate.e2e.services.CounterClient
import my.restate.e2e.services.EventHandlerClient
import my.restate.e2e.services.TestEvent
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

private const val COUNTER_TOPIC = "counter"
private const val EVENT_HANDLER_TOPIC = "event-handler"

private fun kafkaClusterOptions(): RestateConfigSchema {
  return RestateConfigSchema()
      .withIngress(
          IngressOptions()
              .withKafkaClusters(
                  listOf(
                      KafkaClusterOptions()
                          .withName("my-cluster")
                          .withBrokers(listOf("PLAINTEXT://kafka:9092")))))
}

class JavaKafkaIngressTest : BaseKafkaIngressTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-counter",
                        CounterClient.SERVICE_NAME,
                        EventHandlerClient.SERVICE_NAME))
                .withContainer("kafka", KafkaContainer(COUNTER_TOPIC, EVENT_HANDLER_TOPIC))
                .withConfig(kafkaClusterOptions())
                .build())
  }
}

class NodeKafkaIngressTest : BaseKafkaIngressTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-counter",
                        CounterClient.SERVICE_NAME,
                        EventHandlerClient.SERVICE_NAME))
                .withContainer("kafka", KafkaContainer(COUNTER_TOPIC, EVENT_HANDLER_TOPIC))
                .withConfig(kafkaClusterOptions())
                .build())
  }
}

abstract class BaseKafkaIngressTest {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun handleEventInCounterService(
      @InjectMetaURL metaURL: URL,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.EXTERNAL_PORT) kafkaPort: Int,
      @InjectIngressClient ingressClient: IngressClient
  ) {
    val counter = UUID.randomUUID().toString()

    // Create subscription
    val subscriptionsClient =
        SubscriptionApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    subscriptionsClient.createSubscription(
        CreateSubscriptionRequest()
            .source("kafka://my-cluster/$COUNTER_TOPIC")
            .sink("service://${CounterClient.SERVICE_NAME}/add")
            .options(mapOf("auto.offset.reset" to "earliest")))

    // Produce message to kafka
    produceMessageToKafka(
        "PLAINTEXT://localhost:$kafkaPort",
        COUNTER_TOPIC,
        listOf(counter to "1", counter to "2", counter to "3"))

    // Now wait for the update to be visible
    await untilCallTo
        {
          CounterClient.fromIngress(ingressClient, counter).get()
        } matches
        { num ->
          num!! == 6L
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun handleEventInEventHandler(
      @InjectMetaURL metaURL: URL,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.EXTERNAL_PORT) kafkaPort: Int,
      @InjectIngressClient ingressClient: IngressClient
  ) {
    val counter = UUID.randomUUID().toString()

    // Create subscription
    val subscriptionsClient =
        SubscriptionApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    subscriptionsClient.createSubscription(
        CreateSubscriptionRequest()
            .source("kafka://my-cluster/$EVENT_HANDLER_TOPIC")
            .sink("service://${EventHandlerClient.SERVICE_NAME}/handle")
            .options(mapOf("auto.offset.reset" to "earliest")))

    // Produce message to kafka
    produceMessageToKafka(
        "PLAINTEXT://localhost:$kafkaPort",
        EVENT_HANDLER_TOPIC,
        listOf(
            null to Utils.writeValueAsStringUsingJackson(TestEvent(counter, 1)),
            null to Utils.writeValueAsStringUsingJackson(TestEvent(counter, 2)),
            null to Utils.writeValueAsStringUsingJackson(TestEvent(counter, 3))))

    // Now wait for the update to be visible
    await untilCallTo
        {
          CounterClient.fromIngress(ingressClient, counter).get()
        } matches
        { num ->
          num!! == 6L
        }
  }
}

private fun produceMessageToKafka(
    bootstrapServer: String,
    topic: String,
    values: List<Pair<String?, String>>
) {
  val props = Properties()
  props["bootstrap.servers"] = bootstrapServer
  props["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
  props["value.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"

  val producer: Producer<String, String> = KafkaProducer(props)
  for (value in values) {
    producer.send(ProducerRecord(topic, value.first, value.second))
  }
  producer.close()
}
