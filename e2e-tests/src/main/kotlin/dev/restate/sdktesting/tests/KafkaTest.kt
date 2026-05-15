// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.KafkaClusterApi
import dev.restate.admin.api.SubscriptionApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.CreateKafkaClusterRequest
import dev.restate.admin.model.CreateSubscriptionRequest
import dev.restate.client.Client
import dev.restate.client.kotlin.attachSuspend
import dev.restate.client.kotlin.getOutputSuspend
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.client.kotlin.toWorkflow
import dev.restate.client.kotlin.workflowHandle
import dev.restate.common.reflections.ReflectionUtils.extractServiceName
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.common.StateKey
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.durablePromiseKey
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.promise
import dev.restate.sdk.kotlin.promiseHandle
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdk.kotlin.stateKey
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectContainerPort
import dev.restate.sdktesting.infra.KafkaContainer
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import dev.restate.sdktesting.tests.Tracing.JAEGER_HOSTNAME
import dev.restate.sdktesting.tests.Tracing.JAEGER_QUERY_PORT
import java.net.URI
import java.util.Properties
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated

@Isolated
class KafkaTest {

  @Workflow
  class MyWorkflow {
    companion object {
      val PROMISE = durablePromiseKey<String>("promise")
    }

    @Workflow suspend fun run(myTask: String) = "Run $myTask"

    @Shared
    suspend fun setPromise(myValue: String) {
      promiseHandle(PROMISE).resolve(myValue)
    }

    @Shared suspend fun getPromise() = promise(PROMISE).future().await()
  }

  @VirtualObject
  @Name("Counter")
  class Counter {
    companion object {
      private val COUNTER_KEY: StateKey<Long> = stateKey("counter")
    }

    @Handler
    suspend fun add(value: Long): Long {
      val current = state().get(COUNTER_KEY) ?: 0L
      val newValue = current + value
      state().set(COUNTER_KEY, newValue)
      return newValue
    }

    @Handler suspend fun get(): Long = state().get(COUNTER_KEY) ?: 0L
  }

  @Service
  @Name("EventHandler")
  class EventHandler {
    @Serializable data class ProxyRequest(val key: String, val value: Long)

    @Handler
    suspend fun oneWayCall(request: ProxyRequest) {
      dev.restate.sdk.kotlin
          .toVirtualObject<Counter>(request.key)
          .request { add(request.value) }
          .send()
    }
  }

  @VirtualObject
  @Name("TracingCounter")
  class TracingCounter {
    @Handler
    suspend fun set(value: String) {
      check(state().get<String>("state") == null)
      state().set("state", value)
    }

    @Shared suspend fun get() = state().get<String>("state")
  }

  companion object {
    private const val WORKFLOW_TOPIC = "workflow"
    private const val SHARED_HANDLER_TOPIC = "shared-handler"
    private const val COUNTER_TOPIC = "counter"
    private const val EVENT_HANDLER_TOPIC = "event-handler"
    private const val TRACING_TOPIC = "tracing"

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withContainer(Tracing.jaegerContainer())
      withContainer(
          "kafka",
          KafkaContainer(
              WORKFLOW_TOPIC,
              SHARED_HANDLER_TOPIC,
              COUNTER_TOPIC,
              EVENT_HANDLER_TOPIC,
              TRACING_TOPIC))
      withConfig(RestateConfigSchema().apply(Tracing.configSchema))
      withEndpoint(
          Endpoint.bind(MyWorkflow()).bind(Counter()).bind(EventHandler()).bind(TracingCounter()))
    }

    fun produceMessagesToKafka(port: Int, topic: String, values: List<Pair<String?, String>>) {
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

    fun registerKafkaCluster(
        adminURI: URI,
    ) {
      val kafkaClustersClient =
          KafkaClusterApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))
      retryOnServiceUnavailable {
        kafkaClustersClient.createKafkaCluster(
            CreateKafkaClusterRequest()
                .name("my-cluster")
                .properties(
                    mapOf(
                        "bootstrap.servers" to
                            "PLAINTEXT://kafka:${KafkaContainer.KAFKA_NETWORK_PORT}")))
      }
    }

    fun createKafkaSubscription(
        adminURI: URI,
        topic: String,
        serviceName: String,
        handlerName: String
    ) {
      val subscriptionsClient =
          SubscriptionApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))
      retryOnServiceUnavailable {
        subscriptionsClient.createSubscription(
            CreateSubscriptionRequest()
                .source(URI.create("kafka://my-cluster/$topic"))
                .sink(URI.create("service://$serviceName/$handlerName"))
                .options(mapOf("auto.offset.reset" to "earliest")))
      }
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
  fun callWorkflowHandler(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    createKafkaSubscription(
        adminURI, WORKFLOW_TOPIC, extractServiceName(MyWorkflow::class.java), "run")

    val keyMessages = linkedMapOf("a" to "1", "b" to "2", "c" to "3")

    produceMessagesToKafka(
        kafkaPort, WORKFLOW_TOPIC, keyMessages.map { it.key to Json.encodeToString(it.value) })

    for (keyMessage in keyMessages) {
      await withAlias
          "Workflow invocations from Kafka" untilAsserted
          {
            assertThat(
                    ingressClient
                        .workflowHandle<String>(
                            extractServiceName(MyWorkflow::class.java), keyMessage.key)
                        .attachSuspend()
                        .response)
                .isEqualTo("Run ${keyMessage.value}")
          }
    }

    for (keyMessage in keyMessages) {
      await withAlias
          "Workflow invocations from Kafka" untilAsserted
          {
            assertThat(
                    ingressClient
                        .workflowHandle<String>(
                            extractServiceName(MyWorkflow::class.java), keyMessage.key)
                        .getOutputSuspend()
                        .response
                        .value)
                .isEqualTo("Run ${keyMessage.value}")
          }
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun callSharedWorkflowHandler(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    createKafkaSubscription(
        adminURI, SHARED_HANDLER_TOPIC, extractServiceName(MyWorkflow::class.java), "setPromise")

    val keyMessages = linkedMapOf("a" to "a", "b" to "b", "c" to "c")

    produceMessagesToKafka(
        kafkaPort,
        SHARED_HANDLER_TOPIC,
        keyMessages.map { it.key to Json.encodeToString(it.value) })

    for (keyMessage in keyMessages) {
      await withAlias
          "Workflow invocations from Kafka" untilAsserted
          {
            assertThat(
                    ingressClient
                        .toWorkflow<MyWorkflow>(keyMessage.key)
                        .request { getPromise() }
                        .call()
                        .response)
                .isEqualTo(keyMessage.value)
          }
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun callObjectHandler(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    val counter = UUID.randomUUID().toString()

    createKafkaSubscription(adminURI, COUNTER_TOPIC, "Counter", "add")

    produceMessagesToKafka(
        kafkaPort, COUNTER_TOPIC, listOf(counter to "1", counter to "2", counter to "3"))

    await withAlias
        "Updates from Kafka are visible in the counter" untilAsserted
        {
          assertThat(
                  ingressClient.toVirtualObject<Counter>(counter).request { get() }.call().response)
              .isEqualTo(6L)
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun callServiceHandler(
      @InjectAdminURI adminURI: URI,
      @InjectContainerPort(hostName = "kafka", port = KafkaContainer.KAFKA_EXTERNAL_PORT)
      kafkaPort: Int,
      @InjectClient ingressClient: Client
  ) = runTest {
    val counter = UUID.randomUUID().toString()

    createKafkaSubscription(adminURI, EVENT_HANDLER_TOPIC, "EventHandler", "oneWayCall")

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
          assertThat(
                  ingressClient.toVirtualObject<Counter>(counter).request { get() }.call().response)
              .isEqualTo(6L)
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
    createKafkaSubscription(
        adminURI, TRACING_TOPIC, extractServiceName(TracingCounter::class.java), "set")

    produceMessagesToKafka(kafkaPort, TRACING_TOPIC, listOf("a" to Json.encodeToString("a")))

    val client = ingressClient.toVirtualObject<TracingCounter>("a")
    await withAlias
        "state is updated" untilAsserted
        {
          assertThat(client.request { get() }.call().response).isEqualTo("a")
        }

    await withAlias
        "traces are available" untilAsserted
        {
          val traces = Tracing.getTraces(jaegerPort, "Restate")

          assertThat(traces.result.resourceSpans).isNotEmpty()

          val counterAddSpans =
              traces.result.resourceSpans
                  .flatMap { it.scopeSpans }
                  .flatMap { it.spans }
                  .filter {
                    it.name.contains(
                        "ingress_kafka ${extractServiceName(TracingCounter::class.java)}/{key}/set")
                  }

          assertThat(counterAddSpans).isNotEmpty()

          val span = counterAddSpans.first()
          val attributes = span.attributes.associate { it.key to it.value.stringValue }
          assertThat(attributes)
              .containsEntry(
                  "restate.invocation.target",
                  "${extractServiceName(TracingCounter::class.java)}/a/set")
              .containsEntry("messaging.system", "kafka")
              .containsEntry("messaging.source.name", TRACING_TOPIC)
              .containsEntry("messaging.operation.type", "process")
              .containsKeys(
                  "restate.invocation.id",
                  "messaging.consumer.group.name",
                  "messaging.kafka.offset",
                  "messaging.source.partition.id")
        }
  }
}
