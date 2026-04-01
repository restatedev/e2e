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
import dev.restate.client.kotlin.attachSuspend
import dev.restate.client.kotlin.getOutputSuspend
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toWorkflow
import dev.restate.client.kotlin.workflowHandle
import dev.restate.common.reflections.ReflectionUtils.extractServiceName
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.Workflow
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.durablePromiseKey
import dev.restate.sdk.kotlin.promise
import dev.restate.sdk.kotlin.promiseHandle
import dev.restate.sdktesting.infra.*
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import java.net.URI
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

class KafkaAndWorkflowAPITest {

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

  companion object {
    private const val SHARED_HANDLER_TOPIC = "shared-handler"
    private const val WORKFLOW_TOPIC = "workflow"

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withContainer("kafka", KafkaContainer(SHARED_HANDLER_TOPIC, WORKFLOW_TOPIC))
      withConfig(RestateConfigSchema().apply(Kafka.configSchema))
      withEndpoint(Endpoint.bind(MyWorkflow()))
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
    // Create subscription
    Kafka.createKafkaSubscription(
        adminURI, WORKFLOW_TOPIC, extractServiceName(MyWorkflow::class.java), "run")

    val keyMessages = linkedMapOf("a" to "1", "b" to "2", "c" to "3")

    // Produce message to kafka
    Kafka.produceMessagesToKafka(
        kafkaPort, WORKFLOW_TOPIC, keyMessages.map { it.key to Json.encodeToString(it.value) })

    // Now assert that those invocations are stored there, let's do this twice just for the sake of.
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
    // Create subscription
    Kafka.createKafkaSubscription(
        adminURI, SHARED_HANDLER_TOPIC, extractServiceName(MyWorkflow::class.java), "setPromise")

    val keyMessages = linkedMapOf("a" to "a", "b" to "b", "c" to "c")

    // Produce message to kafka
    Kafka.produceMessagesToKafka(
        kafkaPort,
        SHARED_HANDLER_TOPIC,
        keyMessages.map { it.key to Json.encodeToString(it.value) })

    // Now assert that the promises are fulfilled.
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
}
