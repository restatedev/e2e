// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
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
    val handle = client.submit("Francesco")

    // Wait state is set
    await untilCallTo { client.getState() } matches { it!!.isPresent }

    client.unblock("Till")

    assertThat(handle.attach()).isEqualTo("Till")

    // Can call get output again
    assertThat(handle.output).isEqualTo("Till")

    // Re-submit should have no effect
    assertThat(client.submit("Francesco").output).isEqualTo("Till")
  }
}

@Tag("always-suspending")
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
  fun setAndResolve(@InjectIngressClient ingressClient: IngressClient) {
    val client =
        WorkflowAPIBlockAndWaitClient.fromIngress(ingressClient, UUID.randomUUID().toString())

    val handle = client.submit("Francesco")

    // Wait state is set
    await untilCallTo { client.getState() } matches { it!!.isPresent }

    client.unblock("Till")

    assertThat(handle.attach()).isEqualTo("Till")

    // Can call get output again
    assertThat(handle.output).isEqualTo("Till")

    // Re-submit should have no effect
    assertThat(client.submit("Francesco").output).isEqualTo("Till")
  }
}
