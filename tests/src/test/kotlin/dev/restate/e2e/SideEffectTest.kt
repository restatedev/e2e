package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SideEffectTest {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder().withFunction(Containers.COORDINATOR_FUNCTION_SPEC).build())
  }

  @Test
  fun sideEffectFlush(@InjectBlockingStub coordinatorClient: CoordinatorBlockingStub) {
    assertThat(coordinatorClient.invokeSideEffects(Empty.getDefaultInstance()))
        .extracting { it.invokedTimes }
        .isEqualTo(1)
  }
}
