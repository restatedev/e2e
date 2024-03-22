// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.Containers.javaServicesContainer
import dev.restate.e2e.Containers.nodeServicesContainer
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import my.restate.e2e.services.CoordinatorClient
import my.restate.e2e.services.ReceiverClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
class JavaServiceToServiceCallTest : BaseServiceToServiceCallTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class NodeServiceToServiceCallTest : BaseServiceToServiceCallTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.NODE_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class JavaCoordinatorWithNodeReceiverServiceToServiceCallTest : BaseServiceToServiceCallTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    javaServicesContainer("java-coordinator", CoordinatorClient.COMPONENT_NAME))
                .withServiceEndpoint(
                    nodeServicesContainer("node-coordinator", ReceiverClient.COMPONENT_NAME))
                .build())
  }
}

@Tag("always-suspending")
class NodeCoordinatorWithJavaReceiverServiceToServiceCallTest : BaseServiceToServiceCallTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    nodeServicesContainer("node-coordinator", CoordinatorClient.COMPONENT_NAME))
                .withServiceEndpoint(
                    javaServicesContainer("java-coordinator", ReceiverClient.COMPONENT_NAME))
                .build())
  }
}

abstract class BaseServiceToServiceCallTest {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun synchronousCall(@InjectIngressClient ingressClient: IngressClient) {
    assertThat(CoordinatorClient.fromIngress(ingressClient).proxy()).isEqualTo("pong")
  }
}
