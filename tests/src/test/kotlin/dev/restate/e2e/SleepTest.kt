package dev.restate.e2e

import dev.restate.e2e.Containers.FIXED_DELAY_RETRY_POLICY
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.FunctionSpec.RegistrationOptions
import io.grpc.Channel
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaSleepTest : BaseSleepTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(
              Containers.JAVA_COORDINATOR_FUNCTION_SPEC.copy(
                  hostName = COORDINATOR_HOSTNAME,
                  registrationOptions =
                      RegistrationOptions(retryPolicy = FIXED_DELAY_RETRY_POLICY)))
          .build()
    }
  }
}

class NodeSleepTest : BaseSleepTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(
              Containers.NODE_COORDINATOR_FUNCTION_SPEC.copy(
                  hostName = COORDINATOR_HOSTNAME,
                  registrationOptions =
                      RegistrationOptions(retryPolicy = FIXED_DELAY_RETRY_POLICY)))
          .build()
    }
  }

  @Disabled("https://github.com/restatedev/sdk-typescript/issues/27")
  override fun sleepAndKillServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    super.sleepAndKillServiceEndpoint(runtimeChannel, coordinatorContainer)
  }
}

abstract class BaseSleepTest {

  companion object {
    internal const val COORDINATOR_HOSTNAME = "coordinator"
  }

  @Test
  fun sleep(@InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub) {
    val sleepDuration = 100.milliseconds

    val elapsed = measureNanoTime {
      coordinatorClient.sleep(
          CoordinatorProto.Duration.newBuilder()
              .setMillis(sleepDuration.inWholeMilliseconds)
              .build())
    }

    assertThat(elapsed.nanoseconds).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @Test
  open fun sleepAndKillServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    val sleepDuration = 4.seconds

    val elapsed = measureNanoTime {
      val fut =
          CoordinatorGrpc.newFutureStub(runtimeChannel)
              .sleep(
                  CoordinatorProto.Duration.newBuilder()
                      .setMillis(sleepDuration.inWholeMilliseconds)
                      .build())

      Thread.sleep(
          Random.nextLong(
              (sleepDuration / 4).inWholeMilliseconds..(sleepDuration / 2).inWholeMilliseconds))
      coordinatorContainer.killAndRestart()

      fut.get()
    }

    assertThat(elapsed.nanoseconds).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @Test
  fun sleepAndTerminateServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    val sleepDuration = 4.seconds

    val elapsed = measureNanoTime {
      val fut =
          CoordinatorGrpc.newFutureStub(runtimeChannel)
              .sleep(
                  CoordinatorProto.Duration.newBuilder()
                      .setMillis(sleepDuration.inWholeMilliseconds)
                      .build())

      Thread.sleep(
          Random.nextLong(
              (sleepDuration / 4).inWholeMilliseconds..(sleepDuration / 2).inWholeMilliseconds))
      coordinatorContainer.terminateAndRestart()

      fut.get()
    }

    assertThat(elapsed.nanoseconds).isGreaterThanOrEqualTo(sleepDuration)
  }
}
