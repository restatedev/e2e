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
import my.restate.e2e.services.SideEffectClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("only-always-suspending")
class JavaSideEffectTest : BaseSideEffectTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-side-effect", SideEffectClient.COMPONENT_NAME))
                .build())
  }
}

@Tag("only-always-suspending")
class NodeSideEffectTest : BaseSideEffectTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-side-effect", SideEffectClient.COMPONENT_NAME))
                .build())
  }
}

abstract class BaseSideEffectTest {
  @DisplayName("Side effect should wait on acknowledgements")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectFlush(@InjectIngressClient ingressClient: IngressClient) {
    assertThat(SideEffectClient.fromIngress(ingressClient).invokeSideEffects()).isEqualTo(0)
  }
}
