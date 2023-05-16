package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.functions.receiver.ReceiverGrpc
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.time.Duration
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaCoordinatorTest : BaseCoordinatorTest() {
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

    assertThat(response.timeoutOccurred).isTrue
  }
}

@Tag("always-suspending")
class NodeCoordinatorTest : BaseCoordinatorTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_COORDINATOR_FUNCTION_SPEC)
                .build())
  }
}

class JavaCoordinatorWithNodeReceiverTest : BaseCoordinatorTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-coordinator", CoordinatorGrpc.SERVICE_NAME))
                .withServiceEndpoint(
                    Containers.nodeServicesContainer("node-coordinator", ReceiverGrpc.SERVICE_NAME))
                .build())
  }
}

class NodeCoordinatorWithJavaReceiverTest : BaseCoordinatorTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-coordinator", CoordinatorGrpc.SERVICE_NAME))
                .withServiceEndpoint(
                    Containers.javaServicesContainer("java-coordinator", ReceiverGrpc.SERVICE_NAME))
                .build())
  }
}

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
