// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.services.coordinator.CoordinatorGrpc
import dev.restate.e2e.services.coordinator.CoordinatorProto
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.InjectChannel
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import io.grpc.Channel
import java.time.Duration
import my.restate.e2e.services.CoordinatorClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
class JavaOldAwaitTimeoutTest : BaseOldAwaitTimeoutTest() {
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
class NodeOldAwaitTimeoutTest : BaseOldAwaitTimeoutTest() {
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
abstract class BaseOldAwaitTimeoutTest {

  @Test
  @DisplayName("Test Awaitable#await(Duration)")
  @Execution(ExecutionMode.CONCURRENT)
  fun timeout(@InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub) {
    val timeout = Duration.ofMillis(100L)
    val response =
        coordinatorClient.timeout(
            CoordinatorProto.Duration.newBuilder().setMillis(timeout.toMillis()).build())

    assertThat(response.timeoutOccurred).isTrue
  }
}

@Tag("always-suspending")
class JavaAwaitTimeoutTest : BaseAwaitTimeoutTest() {
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
abstract class BaseAwaitTimeoutTest {
  @Test
  @DisplayName("Test Awaitable#await(Duration)")
  @Execution(ExecutionMode.CONCURRENT)
  fun timeout(@InjectChannel channel: Channel) {
    val timeout = Duration.ofMillis(100L)

    assertThat(CoordinatorClient.fromIngress(channel).timeout(timeout.toMillis())).isTrue
  }
}
