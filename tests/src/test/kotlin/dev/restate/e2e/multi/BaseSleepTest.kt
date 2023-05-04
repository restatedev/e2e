package dev.restate.e2e.multi

import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.utils.InjectBlockingStub
import java.time.Duration
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

abstract class BaseSleepTest {

  @Test
  fun sleep(@InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub) {
    val sleepDuration = Duration.ofMillis(10L)

    val elapsed = measureNanoTime {
      coordinatorClient.sleep(
          CoordinatorProto.Duration.newBuilder().setMillis(sleepDuration.toMillis()).build())
    }

    Assertions.assertThat(Duration.ofNanos(elapsed)).isGreaterThanOrEqualTo(sleepDuration)
  }
}
