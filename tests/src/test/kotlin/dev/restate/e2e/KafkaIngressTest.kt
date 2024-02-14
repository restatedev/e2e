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
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.counterRequest
import dev.restate.e2e.services.eventhandler.EventHandlerGrpc
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.config.*
import io.grpc.MethodDescriptor
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

private const val COUNTER_TOPIC = "counter"
private const val EVENT_HANDLER_TOPIC = "event-handler"

private fun kafkaClusterOptions(): RestateConfigSchema {
  return RestateConfigSchema()
      .withWorker(
          WorkerOptions()
              .withKafka(
                  SubscriptionOptions()
                      .withClusters(
                          Clusters()
                              .withAdditionalProperty(
                                  "my-cluster",
                                  KafkaClusterOptions()
                                      .withMetadataBrokerList("PLAINTEXT://kafka:9092")))))
}

class JavaKafkaIngressTest : BaseKafkaIngressTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-counter", CounterGrpc.SERVICE_NAME, EventHandlerGrpc.SERVICE_NAME))
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
                        "node-counter", CounterGrpc.SERVICE_NAME, EventHandlerGrpc.SERVICE_NAME))
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
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    counterEventsTest(
        metaURL, kafkaPort, counterClient, COUNTER_TOPIC, CounterGrpc.getHandleEventMethod())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun handleEventInEventHandler(
      @InjectMetaURL metaURL: URL,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.EXTERNAL_PORT) kafkaPort: Int,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    counterEventsTest(
        metaURL, kafkaPort, counterClient, EVENT_HANDLER_TOPIC, EventHandlerGrpc.getHandleMethod())
  }

  fun counterEventsTest(
      metaURL: URL,
      kafkaPort: Int,
      counterClient: CounterBlockingStub,
      topic: String,
      methodDescriptor: MethodDescriptor<*, *>
  ) {
    val counter = UUID.randomUUID().toString()

    // Create subscription
    val subscriptionsClient =
        SubscriptionApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    subscriptionsClient.createSubscription(
        CreateSubscriptionRequest()
            .source("kafka://my-cluster/$topic")
            .sink("service://${methodDescriptor.fullMethodName}")
            .options(mapOf("auto.offset.reset" to "earliest")))

    // Produce message to kafka
    produceMessageToKafka("PLAINTEXT://localhost:$kafkaPort", topic, counter, listOf("1", "2", "3"))

    // Now wait for the update to be visible
    await untilCallTo
        {
          counterClient.get(counterRequest { counterName = counter })
        } matches
        { num ->
          num!!.value == 6L
        }
  }
}

class NodeHandlerAPIKafkaIngressTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-counter", Containers.HANDLER_API_COUNTER_SERVICE_NAME))
                .withContainer("kafka", KafkaContainer(COUNTER_TOPIC))
                .withConfig(kafkaClusterOptions())
                .build())
  }

  @Test
  fun handleKeyedEvent(
      @InjectMetaURL metaURL: URL,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.EXTERNAL_PORT) kafkaPort: Int,
      @InjectGrpcIngressURL httpEndpointURL: URL
  ) {
    val counter = UUID.randomUUID().toString()

    // Create subscription
    val subscriptionsClient =
        SubscriptionApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    subscriptionsClient.createSubscription(
        CreateSubscriptionRequest()
            .source("kafka://my-cluster/$COUNTER_TOPIC")
            .sink("service://${Containers.HANDLER_API_COUNTER_SERVICE_NAME}/handleEvent")
            .options(mapOf("auto.offset.reset" to "earliest")))

    // Produce message to kafka
    produceMessageToKafka(
        "PLAINTEXT://localhost:$kafkaPort", COUNTER_TOPIC, counter, listOf("1", "2", "3"))

    val client = HttpClient.newHttpClient()

    // Now wait for the update to be visible
    await untilAsserted
        {
          val req =
              HttpRequest.newBuilder(
                      URI.create(
                          "${httpEndpointURL}${Containers.HANDLER_API_COUNTER_SERVICE_NAME}/get"))
                  .POST(JsonUtils.jacksonBodyPublisher(mapOf("key" to counter)))
                  .headers("Content-Type", "application/json")
                  .build()

          val response = client.send(req, JsonUtils.jacksonBodyHandler())

          assertThat(response.statusCode()).isEqualTo(200)
          assertThat(response.headers().firstValue("content-type"))
              .get()
              .asString()
              .contains("application/json")
          assertThat(response.body().get("response").get("counter").asInt()).isEqualTo(6)
        }
  }
}

private fun produceMessageToKafka(
    bootstrapServer: String,
    topic: String,
    key: String,
    values: List<String>
) {
  val props = Properties()
  props["bootstrap.servers"] = bootstrapServer
  props["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
  props["value.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"

  val producer: Producer<String, String> = KafkaProducer(props)
  for (value in values) {
    producer.send(ProducerRecord(topic, key, value))
  }
  producer.close()
}
