package dev.restate.e2e

import com.google.protobuf.Message
import io.cloudevents.CloudEvent
import io.cloudevents.core.builder.CloudEventBuilder
import java.net.URI
import java.util.*
import org.apache.kafka.clients.producer.ProducerRecord

/** Create a new Kafka record to invoke a function. */
fun newInvocationRecord(
    topic: String,
    serviceName: String,
    functionName: String,
    key: String,
    message: Message
): ProducerRecord<String, CloudEvent> {

  val event: CloudEvent =
      CloudEventBuilder.v1()
          .withId(UUID.randomUUID().toString())
          .withSource(URI.create("https://restate.dev"))
          .withType(message.descriptorForType.fullName)
          .withSubject("$serviceName/$functionName")
          .withData("application/protobuf", message.toByteArray())
          .build()

  return ProducerRecord(topic, key, event)
}
