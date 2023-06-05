package dev.restate.e2e

import dev.restate.e2e.services.coordinator.CoordinatorGrpc
import dev.restate.e2e.services.coordinator.CoordinatorProto
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.time.Duration
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaSampleWorkflowTest : BaseSampleWorkflowTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class NodeSampleWorkflowTest : BaseSampleWorkflowTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

abstract class BaseSampleWorkflowTest {

  @Test
  @DisplayName("Sample workflow with sleep, side effect, call and one way call")
  fun sampleWorkflow(
      @InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub
  ) {
    val sleepDuration = Duration.ofMillis(100L)

    val elapsed = measureNanoTime {
      val value = "foobar"
      val response =
          coordinatorClient.complex(
              CoordinatorProto.ComplexRequest.newBuilder()
                  .setSleepDuration(
                      CoordinatorProto.Duration.newBuilder().setMillis(sleepDuration.toMillis()))
                  .setRequestValue(value)
                  .build())

      assertThat(response.responseValue).isEqualTo(value)
    }

    assertThat(Duration.ofNanos(elapsed)).isGreaterThanOrEqualTo(sleepDuration)
  }
}
