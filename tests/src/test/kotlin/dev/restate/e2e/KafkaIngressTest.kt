package dev.restate.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.counterRequest
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.config.*
import dev.restate.e2e.utils.meta.client.SubscriptionsClient
import dev.restate.e2e.utils.meta.models.CreateSubscriptionRequest
import java.net.URL
import java.util.*
import okhttp3.OkHttpClient
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val TOPIC = "my-topic"

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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .withContainer("kafka", KafkaContainer(TOPIC))
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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_COUNTER_SERVICE_SPEC)
                .withContainer("kafka", KafkaContainer(TOPIC))
                .withConfig(kafkaClusterOptions())
                .build())
  }
}

abstract class BaseKafkaIngressTest {

  private fun produceMessageToKafka(
      bootstrapServer: String,
      topic: String,
      key: String,
      value: String
  ) {
    val props = Properties()
    props["bootstrap.servers"] = bootstrapServer
    props["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
    props["value.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"

    val producer: Producer<String, String> = KafkaProducer(props)
    producer.send(ProducerRecord(topic, key, value))
    producer.close()
  }

  @Test
  fun handleKeyedEvent(
      @InjectMetaURL metaURL: URL,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.EXTERNAL_PORT) kafkaPort: Int,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    val counter = UUID.randomUUID().toString()

    // Produce message to kafka
    produceMessageToKafka("PLAINTEXT://localhost:$kafkaPort", TOPIC, counter, "123")

    // Create subscription
    val subscriptionsClient =
        SubscriptionsClient(ObjectMapper(), metaURL.toString(), OkHttpClient())
    assertThat(
            subscriptionsClient.createSubscription(
                CreateSubscriptionRequest(
                    source = "kafka://my-cluster/$TOPIC",
                    sink =
                        "service://${CounterGrpc.SERVICE_NAME}/${CounterGrpc.getHandleEventMethod().bareMethodName}",
                    options = mapOf("auto.offset.reset" to "earliest"))))
        .extracting { it.statusCode }
        .isEqualTo(201)

    // Now wait for the update to be visible
    await untilCallTo
        {
          counterClient.get(counterRequest { counterName = counter })
        } matches
        { num ->
          num!!.value == 123L
        }
  }
}
