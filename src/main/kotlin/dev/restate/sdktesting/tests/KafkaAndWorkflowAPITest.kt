// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.SubscriptionApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.CreateSubscriptionRequest
import dev.restate.client.Client
import dev.restate.client.kotlin.attachSuspend
import dev.restate.client.kotlin.getOutputSuspend
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdktesting.infra.*
import dev.restate.sdktesting.infra.runtimeconfig.IngressOptions
import dev.restate.sdktesting.infra.runtimeconfig.KafkaClusterOptions
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import java.net.URI
import java.util.*
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
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

class KafkaAndWorkflowAPITest {

  @Workflow
  class MyWorkflow {
    @Workflow suspend fun run(ctx: WorkflowContext, myTask: String) = "Run $myTask"
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withContainer("kafka", KafkaContainer(COUNTER_TOPIC, EVENT_HANDLER_TOPIC))
      withConfig(kafkaClusterOptions())
      withEndpoint(Endpoint.bind(MyWorkflow()))
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun handleEventInEventHandler(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.EXTERNAL_PORT) kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    val counter = UUID.randomUUID().toString()

    // Create subscription
    val subscriptionsClient =
        SubscriptionApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))
    subscriptionsClient.createSubscription(
        CreateSubscriptionRequest()
            .source("kafka://my-cluster/$EVENT_HANDLER_TOPIC")
            .sink(
                "service://${KafkaAndWorkflowAPITestMyWorkflowHandlers.Metadata.SERVICE_NAME}/run")
            .options(mapOf("auto.offset.reset" to "earliest")))

    val keyMessages = linkedMapOf("a" to "1", "b" to "2", "c" to "3")

    // Produce message to kafka
    produceMessageToKafka(
        "PLAINTEXT://localhost:$kafkaPort",
        EVENT_HANDLER_TOPIC,
        keyMessages.map { it.key to Json.encodeToString(it.value) })

    // Now assert that those invocations are stored there, let's do this twice just for the sake of.
    for (keyMessage in keyMessages) {
      await withAlias
          "Workflow invocations from Kafka" untilAsserted
          {
            assertThat(
                    KafkaAndWorkflowAPITestMyWorkflowClient.fromClient(
                            ingressClient, keyMessage.key)
                        .workflowHandle()
                        .attachSuspend()
                        .response())
                .isEqualTo("Run ${keyMessage.value}")
          }
    }

    for (keyMessage in keyMessages) {
      await withAlias
          "Workflow invocations from Kafka" untilAsserted
          {
            assertThat(
                    KafkaAndWorkflowAPITestMyWorkflowClient.fromClient(
                            ingressClient, keyMessage.key)
                        .workflowHandle()
                        .getOutputSuspend()
                        .response()
                        .value)
                .isEqualTo("Run ${keyMessage.value}")
          }
    }
  }
}

private fun produceMessageToKafka(
    bootstrapServer: String,
    topic: String,
    values: List<Pair<String, String>>
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
