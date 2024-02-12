// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.InjectChannel
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.workflow.generated.WorkflowExecutionState
import io.grpc.Channel
import java.util.*
import my.restate.e2e.services.WorkflowAPIBlockAndWait
import my.restate.e2e.services.WorkflowAPIBlockAndWaitExternalClient
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

// @Tag("always-suspending")
// class NodeWorkflowAPITest : BaseWorkflowAPITest() {
//  companion object {
//    @RegisterExtension
//    val deployerExt: RestateDeployerExtension =
//        RestateDeployerExtension(
//            RestateDeployer.Builder()
//                .withServiceEndpoint(Containers.NODE_COORDINATOR_SERVICE_SPEC)
//                .build())
//  }
// }

@Tag("always-suspending")
abstract class BaseWorkflowAPITest {

  @Test
  @DisplayName("Set and resolve durable promise leads to completion")
  @Execution(ExecutionMode.CONCURRENT)
  fun setAndResolve(@InjectChannel restateChannel: Channel) {
    val client = WorkflowAPIBlockAndWaitExternalClient(restateChannel, UUID.randomUUID().toString())
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
  fun manySubmit(@InjectChannel restateChannel: Channel) {
    val client = WorkflowAPIBlockAndWaitExternalClient(restateChannel, UUID.randomUUID().toString())
    assertThat(client.submit("Francesco")).isEqualTo(WorkflowExecutionState.STARTED)
    assertThat(client.submit("Francesco")).isEqualTo(WorkflowExecutionState.ALREADY_STARTED)
  }
}
