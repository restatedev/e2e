// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.InjectClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.Client
import dev.restate.sdk.client.SendResponse.SendStatus
import java.util.*
import my.restate.e2e.services.WorkflowAPIBlockAndWaitClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
class JavaWorkflowAPITest : BaseWorkflowAPITest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_WORKFLOW_SERVICE_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class NodeWorkflowAPITest : BaseWorkflowAPITest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.NODE_WORKFLOW_SERVICE_SPEC)
                .build())
  }
}

abstract class BaseWorkflowAPITest {
  @Test
  @DisplayName("Set and resolve durable promise leads to completion")
  @Execution(ExecutionMode.CONCURRENT)
  fun setAndResolve(@InjectClient ingressClient: Client) {
    val client =
        WorkflowAPIBlockAndWaitClient.fromClient(ingressClient, UUID.randomUUID().toString())

    val sendResponse = client.submit("Francesco")
    assertThat(sendResponse.status).isEqualTo(SendStatus.ACCEPTED)

    // Wait state is set
    await untilCallTo { client.getState() } matches { it!!.isPresent }

    client.unblock("Till")

    assertThat(client.workflowHandle().attach()).isEqualTo("Till")

    // Can call get output again
    assertThat(client.workflowHandle().output.value).isEqualTo("Till")

    // Re-submit should have no effect
    val secondSendResponse = client.submit("Francesco")
    assertThat(secondSendResponse.status).isEqualTo(SendStatus.PREVIOUSLY_ACCEPTED)
    assertThat(secondSendResponse.invocationId).isEqualTo(sendResponse.invocationId)
    assertThat(client.workflowHandle().output.value).isEqualTo("Till")
  }
}
