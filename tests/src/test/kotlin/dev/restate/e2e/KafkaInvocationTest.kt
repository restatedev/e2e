package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.utils.KafkaContainer
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.e2e.utils.RestateDeployerExtension.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployerExtension.InjectContainerAddress
import io.cloudevents.CloudEvent
import io.cloudevents.core.message.Encoding
import io.cloudevents.kafka.CloudEventSerializer
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

class KafkaInvocationTest {

  companion object {
    const val TEST_TOPIC = "my-test-topic"
    const val KEY_A = "a"
    const val KEY_B = "b"

    private val logger = LoggerFactory.getLogger(KafkaInvocationTest::class.java)

    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .function("e2e-counter")
                .withContainer("kafka", KafkaContainer(TEST_TOPIC))
                .withConfigEntries(
                    "kafka",
                    mapOf(
                        "topics" to listOf(TEST_TOPIC),
                        "group.id" to "test-consumer",
                        "bootstrap.servers" to "kafka:9092",
                        "enable.partition.eof" to "false",
                        "auto.offset.reset" to "earliest"))
                .build())
  }

  @Test
  fun incrementCounterFromKafka(
      @InjectBlockingStub("e2e-counter", KEY_A) counterClientA: CounterBlockingStub,
      @InjectBlockingStub("e2e-counter", KEY_B) counterClientB: CounterBlockingStub,
      @InjectContainerAddress("kafka", KafkaContainer.PORT) kafkaAddress: String
  ) {
    createProducer(kafkaAddress).use { producer ->
      // Produce messages
      for (i in 0 until 3) {
        logger.debug(
            "Produced message to {}",
            producer
                .send(
                    newInvocationRecord(
                        TEST_TOPIC,
                        CounterGrpc.SERVICE_NAME,
                        "Add",
                        KEY_A,
                        dev.restate.e2e.functions.counter.Number.newBuilder().setValue(1).build()))
                .get())
      }
      for (i in 0 until 5) {
        logger.debug(
            "Produced message to {}",
            producer
                .send(
                    newInvocationRecord(
                        TEST_TOPIC,
                        CounterGrpc.SERVICE_NAME,
                        "Add",
                        KEY_B,
                        dev.restate.e2e.functions.counter.Number.newBuilder().setValue(1).build()))
                .get())
      }

      producer.flush()
    }
    await untilCallTo
        {
          counterClientA.get(Empty.getDefaultInstance())
        } matches
        { num ->
          num!!.value == 3L
        }
    await untilCallTo
        {
          counterClientB.get(Empty.getDefaultInstance())
        } matches
        { num ->
          num!!.value == 5L
        }
  }

  private fun createProducer(kafkaAddress: String): KafkaProducer<String, CloudEvent> {
    val props = Properties()
    props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = "PLAINTEXT://${kafkaAddress}"
    props[ProducerConfig.CLIENT_ID_CONFIG] = "test-producer"
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = CloudEventSerializer::class.java
    // Configure the CloudEventSerializer to emit events as binary events
    props[CloudEventSerializer.ENCODING_CONFIG] = Encoding.BINARY
    return KafkaProducer(props)
  }
}
