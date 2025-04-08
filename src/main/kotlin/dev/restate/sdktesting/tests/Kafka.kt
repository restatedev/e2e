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
import dev.restate.sdktesting.infra.runtimeconfig.IngressOptions
import dev.restate.sdktesting.infra.runtimeconfig.KafkaClusterOptions
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import java.net.URI
import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

object Kafka {
  fun produceMessagesToKafka(port: Int, topic: String, values: List<Pair<String, String>>) {
    val props = Properties()
    props["bootstrap.servers"] = "PLAINTEXT://localhost:$port"
    props["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
    props["value.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"

    val producer: Producer<String, String> = KafkaProducer(props)
    for (value in values) {
      producer.send(ProducerRecord(topic, value.first, value.second))
    }
    producer.close()
  }

  fun createKafkaSubscription(
      adminURI: URI,
      topic: String,
      serviceName: String,
      handlerName: String
  ) {
    val subscriptionsClient =
        SubscriptionApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))
    subscriptionsClient.createSubscription(
        CreateSubscriptionRequest()
            .source("kafka://my-cluster/$topic")
            .sink("service://$serviceName/$handlerName")
            .options(mapOf("auto.offset.reset" to "earliest")))
  }

  val configSchema: RestateConfigSchema.() -> Unit = {
    this.withIngress(
        IngressOptions()
            .withKafkaClusters(
                listOf(
                    KafkaClusterOptions()
                        .withName("my-cluster")
                        .withBrokers(listOf("PLAINTEXT://kafka:9092")))))
  }
}
