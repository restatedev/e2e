package dev.restate.e2e

import dev.restate.e2e.Containers.FIXED_DELAY_RETRY_POLICY
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorGrpcKt
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.FunctionSpec.RegistrationOptions
import io.grpc.Channel
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

// -- Simple sleep tests

@Tag("always-suspending")
class JavaSimpleSleepTest : BaseSimpleSleepTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_FUNCTION_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class NodeSimpleSleepTest : BaseSimpleSleepTest() {
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

abstract class BaseSimpleSleepTest {

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

  @Test
  fun manySleeps(@InjectChannel runtimeChannel: Channel) = runTest {
    val minSleepDuration = 100.milliseconds
    val maxSleepDuration = 150.milliseconds
    val sleepsPerInvocation = 10
    val concurrentSleepInvocations = 50

    val coordinatorClient = CoordinatorGrpcKt.CoordinatorCoroutineStub(runtimeChannel)

    (0..concurrentSleepInvocations)
        .map {
          launch {
            coordinatorClient.manyTimers(
                CoordinatorProto.ManyTimersRequest.newBuilder()
                    .addAllTimer(
                        (1..sleepsPerInvocation)
                            .map {
                              Random.nextLong(
                                  minSleepDuration.inWholeMilliseconds..maxSleepDuration
                                          .inWholeMilliseconds)
                            }
                            .map { CoordinatorProto.Duration.newBuilder().setMillis(it).build() })
                    .build())
          }
        }
        .joinAll()
  }
}

// -- Sleep tests with terminations/killings of runtime/service endpoint

@Tag("always-suspending")
class JavaSleepWithFailuresTest : BaseSleepWithFailuresTest() {
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

  @Disabled("https://github.com/restatedev/sdk-java/issues/85")
  override fun sleepAndKillServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    super.sleepAndKillServiceEndpoint(runtimeChannel, coordinatorContainer)
  }

  @Disabled("https://github.com/restatedev/sdk-java/issues/85")
  override fun sleepAndTerminateServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    super.sleepAndTerminateServiceEndpoint(runtimeChannel, coordinatorContainer)
  }
}

class NodeSleepWithFailuresTest : BaseSleepWithFailuresTest() {
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
}

abstract class BaseSleepWithFailuresTest {

  companion object {
    internal const val COORDINATOR_HOSTNAME = "coordinator"
    private val DEFAULT_SLEEP_DURATION = 4.seconds
  }

  private fun asyncSleepTest(
      runtimeChannel: Channel,
      sleepDuration: Duration = DEFAULT_SLEEP_DURATION,
      action: () -> Unit
  ) {
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
      action()

      fut.get()
    }

    assertThat(elapsed.nanoseconds).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @Test
  open fun sleepAndKillServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    this.asyncSleepTest(runtimeChannel) { coordinatorContainer.killAndRestart() }
  }

  @Timeout(value = 15, unit = TimeUnit.SECONDS)
  @Test
  open fun sleepAndTerminateServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    this.asyncSleepTest(runtimeChannel) { coordinatorContainer.terminateAndRestart() }
  }
}
