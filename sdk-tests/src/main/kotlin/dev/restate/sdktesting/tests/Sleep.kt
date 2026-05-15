// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.client.Client
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAllCompleted
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitOne
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.InterpretRequest
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.Sleep
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.sdktesting.infra.ServiceSpec
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.system.measureNanoTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

class Sleep {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(
          ServiceSpec.defaultBuilder().withServices(VirtualObjectCommandInterpreter::class))
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sleep(@InjectClient ingressClient: Client) = runTest {
    val sleepDuration = 10.milliseconds

    val elapsed = measureNanoTime {
      ingressClient
          .toVirtualObject<VirtualObjectCommandInterpreter>(UUID.randomUUID().toString())
          .request {
            interpretCommands(
                InterpretRequest(listOf(AwaitOne(Sleep(sleepDuration.inWholeMilliseconds)))))
          }
          .options(idempotentCallOptions)
          .call()
    }

    assertThat(elapsed.nanoseconds).isGreaterThanOrEqualTo(sleepDuration)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  @Execution(ExecutionMode.CONCURRENT)
  fun manySleeps(@InjectClient ingressClient: Client) =
      runTest(timeout = 60.seconds) {
        val minSleepDuration = 10.milliseconds
        val maxSleepDuration = 50.milliseconds
        val sleepsPerInvocation = 20
        val concurrentSleepInvocations = 50

        (1..concurrentSleepInvocations)
            .map {
              launch {
                ingressClient
                    .toVirtualObject<VirtualObjectCommandInterpreter>(UUID.randomUUID().toString())
                    .request {
                      interpretCommands(
                          InterpretRequest(
                              listOf(
                                  AwaitAllCompleted(
                                      (1..sleepsPerInvocation).map {
                                        Sleep(
                                            Random.nextLong(
                                                minSleepDuration
                                                    .inWholeMilliseconds..maxSleepDuration
                                                        .inWholeMilliseconds))
                                      }))))
                    }
                    .options(idempotentCallOptions)
                    .call()
              }
            }
            .joinAll()
      }
}
