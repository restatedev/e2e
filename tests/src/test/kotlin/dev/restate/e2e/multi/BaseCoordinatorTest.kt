package dev.restate.e2e.multi

import com.google.protobuf.Empty
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.utils.InjectBlockingStub
import java.time.Duration
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

abstract class BaseCoordinatorTest {

  @Test
  fun invoke_other_function(@InjectBlockingStub coordinatorClient: CoordinatorBlockingStub) {
    val response = coordinatorClient.proxy(Empty.getDefaultInstance())

    assertThat(response.message).isEqualTo("pong")
  }

  @Test
  fun complex_coordination(@InjectBlockingStub coordinatorClient: CoordinatorBlockingStub) {
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
