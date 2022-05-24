package dev.restate.e2e;

import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.time.Duration
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.system.measureNanoTime

class CoordinatorTest {
    companion object {
        @RegisterExtension
        val deployerExt: RestateDeployerExtension = RestateDeployerExtension(
                RestateDeployer.Builder().function("e2e-coordinator").build()
        )
    }

  @Test
  fun sleep(
      @RestateDeployerExtension.InjectBlockingStub("e2e-coordinator")
      coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub
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
}
