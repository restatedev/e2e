package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.coordinator.ComplexRequest
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.time.Duration
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CoordinatorTest {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder().functionSpec(Containers.COORDINATOR_FUNCTION_SPEC).build())
  }

  @Test
  fun sleep(
      @RestateDeployerExtension.InjectBlockingStub("e2e-coordinator")
      coordinatorClient: CoordinatorBlockingStub
  ) {
    val sleepDuration = Duration.ofMillis(10L)

    val elapsed = measureNanoTime {
      coordinatorClient.sleep(
          dev.restate.e2e.functions.coordinator.Duration.newBuilder()
              .setMillis(sleepDuration.toMillis())
              .build())
    }

    assertThat(Duration.ofNanos(elapsed)).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Test
  fun invoke_other_function(
      @RestateDeployerExtension.InjectBlockingStub("e2e-coordinator")
      coordinatorClient: CoordinatorBlockingStub
  ) {
    val response = coordinatorClient.proxy(Empty.getDefaultInstance())

    assertThat(response.message).isEqualTo("pong")
  }

  @Test
  fun complex_coordination(
      @RestateDeployerExtension.InjectBlockingStub("e2e-coordinatator")
      coordinatorClient: CoordinatorBlockingStub
  ) {
    val sleepDuration = Duration.ofMillis(10L)

    val elapsed = measureNanoTime {
      val value = "foobar"
      val response =
          coordinatorClient.complex(ComplexRequest.newBuilder().setRequestValue(value).build())

      assertThat(response.responseValue).isEqualTo(value)
    }

    assertThat(Duration.ofNanos(elapsed)).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Test
  fun timeout(
      @RestateDeployerExtension.InjectBlockingStub("e2e-coordinator")
      coordinatorClient: CoordinatorBlockingStub
  ) {
    val timeout = Duration.ofMillis(10L)
    val response =
        coordinatorClient.timeout(
            dev.restate.e2e.functions.coordinator.Duration.newBuilder()
                .setMillis(timeout.toMillis())
                .build())

    assertThat(response.timeoutOccurred).isTrue
  }
}
