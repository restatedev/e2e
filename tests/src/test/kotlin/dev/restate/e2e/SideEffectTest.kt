// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.services.sideeffect.SideEffectGrpc
import dev.restate.e2e.services.sideeffect.SideEffectGrpc.SideEffectBlockingStub
import dev.restate.e2e.services.sideeffect.SideEffectProto
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

// Note: Java SDK only waits for the ack upon the next blocking operation.
// So the last execution will have 1 as result because the last side effect will get executed and
// then the response gets send back immediately.
@Tag("only-always-suspending")
class JavaSideEffectTest : BaseSideEffectTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-side-effect", SideEffectGrpc.SERVICE_NAME))
                .build())
  }
}

// Note: TS SDK awaits the ack after each side effect.
// So the last execution will have 0 as result because the last side effect will lead to a
// suspension and a replay.
// During the last replay no side effects will be executed anymore.
@Tag("only-always-suspending")
class NodeSideEffectTest : BaseSideEffectTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-side-effect", SideEffectGrpc.SERVICE_NAME))
                .build())
  }
}

abstract class BaseSideEffectTest {
  @DisplayName("Side effect should wait on acknowledgements")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectFlush(@InjectBlockingStub sideEffectStub: SideEffectBlockingStub) {
    assertThat(
            sideEffectStub.invokeSideEffects(
                SideEffectProto.InvokeSideEffectsRequest.newBuilder().build()))
        .extracting { it.nonDeterministicInvocationCount }
        .isEqualTo(0)
  }
}
