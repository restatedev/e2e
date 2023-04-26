package dev.restate.e2e.java

import dev.restate.e2e.Containers
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.multi.BaseCoordinatorTest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.time.Duration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class CoordinatorTest : BaseCoordinatorTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_FUNCTION_SPEC)
                .build())
  }

  @Test
  fun timeout(@InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub) {
    val timeout = Duration.ofMillis(100L)
    val response =
        coordinatorClient.timeout(
            CoordinatorProto.Duration.newBuilder().setMillis(timeout.toMillis()).build())

    Assertions.assertThat(response.timeoutOccurred).isTrue
  }
}
