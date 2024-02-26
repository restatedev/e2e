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
import java.time.Duration
import my.restate.e2e.services.CoordinatorClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

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
@Disabled("node-services is not ready with the new interfaces")
class NodeAwaitTimeoutTest : BaseAwaitTimeoutTest() {
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
abstract class BaseAwaitTimeoutTest {
  @Test
  @DisplayName("Test Awaitable#await(Duration)")
  @Execution(ExecutionMode.CONCURRENT)
  fun timeout(@InjectIngressClient ingressClient: IngressClient) {
    val timeout = Duration.ofMillis(100L)

    assertThat(CoordinatorClient.fromIngress(ingressClient).timeout(timeout.toMillis())).isTrue
  }
}
