// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import com.fasterxml.jackson.databind.JsonNode
import dev.restate.e2e.Utils.doJsonRequestToService
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.InjectIngressURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import dev.restate.sdk.workflow.WorkflowExecutionState
import java.net.URL
import java.util.*
import my.restate.e2e.services.WorkflowAPIBlockAndWait
import my.restate.e2e.services.WorkflowAPIBlockAndWaitClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
class JavaWorkflowAPITest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_WORKFLOW_SERVICE_SPEC)
                .build())
  }

  @Test
  @DisplayName("Set and resolve durable promise leads to completion")
  @Execution(ExecutionMode.CONCURRENT)
  fun setAndResolve(@InjectIngressClient ingressClient: IngressClient) {
    val client =
        WorkflowAPIBlockAndWaitClient.fromIngress(ingressClient, UUID.randomUUID().toString())
    assertThat(client.submit("Francesco")).isEqualTo(WorkflowExecutionState.STARTED)

    // Wait state is set
    await untilCallTo
        {
          client.getState(WorkflowAPIBlockAndWait.MY_STATE)
        } matches
        {
          it!!.isPresent
        }

    client.unblock("Till")

    await untilCallTo { client.output } matches { it!!.orElse("") == "Till" }

    // Can call get output again
    assertThat(client.output).get().isEqualTo("Till")

    // Re-submit returns completed
    assertThat(client.submit("Francesco")).isEqualTo(WorkflowExecutionState.ALREADY_COMPLETED)
  }

  @Test
  @DisplayName("Workflow cannot be submitted more than once")
  @Execution(ExecutionMode.CONCURRENT)
  fun manySubmit(@InjectIngressClient ingressClient: IngressClient) {
    val client =
        WorkflowAPIBlockAndWaitClient.fromIngress(ingressClient, UUID.randomUUID().toString())
    assertThat(client.submit("Francesco")).isEqualTo(WorkflowExecutionState.STARTED)
    assertThat(client.submit("Francesco")).isEqualTo(WorkflowExecutionState.ALREADY_STARTED)
  }
}

@Tag("always-suspending")
@Disabled("node-services is not ready with the new interfaces")
class NodeWorkflowAPITest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.NODE_WORKFLOW_SERVICE_SPEC)
                .build())
  }

  @Test
  @DisplayName("Set and resolve durable promise leads to completion")
  @Execution(ExecutionMode.CONCURRENT)
  fun setAndResolve(@InjectIngressURL httpEndpointURL: URL) {
    val workflowId = UUID.randomUUID().toString()
    assertThat(
            doWorkflowRequest(
                    httpEndpointURL,
                    Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                    "start",
                    workflowId,
                    "input" to "Francesco")
                .asText())
        .isEqualTo("STARTED")

    // Wait state is set
    await untilCallTo
        {
          doWorkflowRequest(
                  httpEndpointURL,
                  Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                  "getState",
                  workflowId)
              .asText()
        } matches
        {
          it == "Francesco"
        }

    // Now unblock
    doWorkflowRequestWithoutResponse(
        httpEndpointURL,
        Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
        "unblock",
        workflowId,
        "output" to "Till")

    // Now wait output
    assertThat(
            doWorkflowRequest(
                    httpEndpointURL,
                    Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                    "waitForResult",
                    workflowId)
                .asText())
        .isEqualTo("Till")

    // Can call get output again
    assertThat(
            doWorkflowRequest(
                    httpEndpointURL,
                    Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                    "waitForResult",
                    workflowId)
                .asText())
        .isEqualTo("Till")

    // Re-submit returns completed
    assertThat(
            doWorkflowRequest(
                    httpEndpointURL,
                    Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                    "start",
                    workflowId,
                    "input" to "Francesco")
                .asText())
        .isEqualTo("ALREADY_FINISHED")
  }

  @Test
  @DisplayName("Workflow cannot be submitted more than once")
  @Execution(ExecutionMode.CONCURRENT)
  fun manySubmit(@InjectIngressURL httpEndpointURL: URL) {
    val workflowId = UUID.randomUUID().toString()
    assertThat(
            doWorkflowRequest(
                    httpEndpointURL,
                    Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                    "start",
                    workflowId,
                    "input" to "Francesco")
                .asText())
        .isEqualTo("STARTED")
    assertThat(
            doWorkflowRequest(
                    httpEndpointURL,
                    Containers.WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME,
                    "start",
                    workflowId,
                    "input" to "Francesco")
                .asText())
        .isEqualTo("ALREADY_STARTED")
  }

  private fun doWorkflowRequest(
      httpEndpointURL: URL,
      workflowName: String,
      method: String,
      workflowId: String,
      vararg payloadEntries: Pair<String, Any>
  ): JsonNode {
    val request = mutableMapOf<String, Any>("workflowId" to workflowId)
    request.putAll(payloadEntries)

    return doJsonRequestToService(
            httpEndpointURL.toString(),
            workflowName,
            method,
            mapOf<String, Any>("request" to request))
        .get("response")
  }

  private fun doWorkflowRequestWithoutResponse(
      httpEndpointURL: URL,
      workflowName: String,
      method: String,
      workflowId: String,
      vararg payloadEntries: Pair<String, Any>
  ) {
    val request = mutableMapOf<String, Any>("workflowId" to workflowId)
    request.putAll(payloadEntries)

    doJsonRequestToService(
        httpEndpointURL.toString(), workflowName, method, mapOf<String, Any>("request" to request))
  }
}
