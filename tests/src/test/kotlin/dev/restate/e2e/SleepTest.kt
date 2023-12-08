// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.services.coordinator.CoordinatorGrpc
import dev.restate.e2e.services.coordinator.CoordinatorGrpcKt
import dev.restate.e2e.services.coordinator.CoordinatorProto
import dev.restate.e2e.utils.*
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

// -- Simple sleep tests

@Tag("always-suspending")
@Tag("timers")
class JavaSimpleSleepTest : BaseSimpleSleepTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

@Tag("always-suspending")
@Tag("timers")
class NodeSimpleSleepTest : BaseSimpleSleepTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.NODE_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

abstract class BaseSimpleSleepTest {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sleep(@InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub) {
    val sleepDuration = 10.milliseconds

    val elapsed = measureNanoTime {
      coordinatorClient.sleep(
          CoordinatorProto.Duration.newBuilder()
              .setMillis(sleepDuration.inWholeMilliseconds)
              .build())
    }

    assertThat(elapsed.nanoseconds).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  @Execution(ExecutionMode.CONCURRENT)
  fun manySleeps(@InjectChannel runtimeChannel: Channel) =
      runTest(timeout = 60.seconds) {
        val minSleepDuration = 10.milliseconds
        val maxSleepDuration = 50.milliseconds
        val sleepsPerInvocation = 20
        val concurrentSleepInvocations = 50

        val coordinatorClient = CoordinatorGrpcKt.CoordinatorCoroutineStub(runtimeChannel)

        // Range is inclusive
        (1..concurrentSleepInvocations)
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
                                .map {
                                  CoordinatorProto.Duration.newBuilder().setMillis(it).build()
                                })
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
          .withServiceEndpoint(
              Containers.JAVA_COORDINATOR_SERVICE_SPEC.copy(name = COORDINATOR_HOSTNAME))
          .build()
    }
  }
}

@Tag("always-suspending")
class NodeSleepWithFailuresTest : BaseSleepWithFailuresTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withServiceEndpoint(
              Containers.NODE_COORDINATOR_SERVICE_SPEC.copy(
                  name = COORDINATOR_HOSTNAME,
              ))
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
  fun sleepAndTerminateServiceEndpoint(
      @InjectChannel runtimeChannel: Channel,
      @InjectContainerHandle(COORDINATOR_HOSTNAME) coordinatorContainer: ContainerHandle
  ) {
    this.asyncSleepTest(runtimeChannel) { coordinatorContainer.terminateAndRestart() }
  }
}
