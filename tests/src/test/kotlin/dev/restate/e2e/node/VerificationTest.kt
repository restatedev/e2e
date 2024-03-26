// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.utils.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.apache.logging.log4j.LogManager
import org.awaitility.kotlin.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

//@Tag("always-suspending")
//class VerificationTest {
//
//  companion object {
//    @JvmStatic
//    @RegisterExtension
//    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
//      RestateDeployer.Builder().withServiceEndpoint(Containers.VERIFICATION_SERVICE_SPEC).build()
//    }
//
//    private const val E2E_VERIFICATION_SEED_ENV = "E2E_VERIFICATION_SEED"
//
//    private val ALPHANUMERIC_ALPHABET: Array<Char> =
//        (('0'..'9').toList() + ('a'..'z').toList() + ('A'..'Z').toList()).toTypedArray()
//
//    private val logger = LogManager.getLogger(VerificationTest::class.java)
//
//    fun generateAlphanumericString(length: Int): String {
//      return List(length) { Random.nextInt(0, ALPHANUMERIC_ALPHABET.size) }
//          .map { ALPHANUMERIC_ALPHABET[it] }
//          .joinToString(separator = "")
//    }
//
//    private val POLL_INTERVAL = 500.milliseconds.toJavaDuration()
//    private val MAX_POLL_TIME = 2.minutes.toJavaDuration()
//
//    fun CommandVerifierBlockingStub.awaitVerify(testParams: String): Unit =
//        await
//            .pollInterval(POLL_INTERVAL)
//            .atMost(MAX_POLL_TIME)
//            .ignoreException(StatusRuntimeException::class)
//            .untilAsserted { this.verify(verificationRequest { params = testParams }) }
//  }
//
//  @Timeout(value = 1, unit = TimeUnit.MINUTES)
//  @Test
//  fun simple(@InjectBlockingStub verifier: CommandVerifierBlockingStub) {
//    val testParams = testParams()
//    verifier.execute(executeRequest { params = testParams })
//
//    verifier.awaitVerify(testParams)
//
//    verifier.clear(clearRequest { params = testParams })
//  }
//
//  @Timeout(value = 1, unit = TimeUnit.MINUTES)
//  @Test
//  fun killingTheServiceEndpoint(
//      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
//      @InjectContainerHandle(Containers.VERIFICATION_SERVICE_HOSTNAME)
//      verificationContainer: ContainerHandle
//  ) {
//    val testParams = testParams()
//
//    verifier.execute(executeRequest { params = testParams })
//
//    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)
//
//    verificationContainer.killAndRestart()
//
//    verifier.awaitVerify(testParams)
//
//    verifier.clear(clearRequest { params = testParams })
//  }
//
//  @Timeout(value = 1, unit = TimeUnit.MINUTES)
//  @Test
//  fun stoppingTheServiceEndpoint(
//      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
//      @InjectContainerHandle(Containers.VERIFICATION_SERVICE_HOSTNAME)
//      verificationContainer: ContainerHandle
//  ) {
//    val testParams = testParams()
//
//    verifier.execute(executeRequest { params = testParams })
//
//    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)
//
//    verificationContainer.terminateAndRestart()
//
//    verifier.awaitVerify(testParams)
//
//    verifier.clear(clearRequest { params = testParams })
//  }
//
//  @Timeout(value = 1, unit = TimeUnit.MINUTES)
//  @Test
//  fun killingTheRuntime(
//      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
//      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
//  ) {
//    val testParams = testParams()
//
//    verifier.execute(executeRequest { params = testParams })
//
//    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)
//
//    runtimeContainer.killAndRestart()
//
//    verifier.awaitVerify(testParams)
//
//    verifier.clear(clearRequest { params = testParams })
//  }
//
//  @Timeout(value = 1, unit = TimeUnit.MINUTES)
//  @Test
//  fun stoppingTheRuntime(
//      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
//      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
//  ) {
//    val testParams = testParams()
//
//    verifier.execute(executeRequest { params = testParams })
//
//    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)
//
//    runtimeContainer.terminateAndRestart()
//
//    verifier.awaitVerify(testParams)
//
//    verifier.clear(clearRequest { params = testParams })
//  }
//
//  @Timeout(value = 2, unit = TimeUnit.MINUTES)
//  @DisplayName("Suspending or returning with an unawaited blocked syncCall should not deadlock")
//  @Test
//  fun unawaitedSelfCall(@InjectBlockingStub interpreter: CommandInterpreterBlockingStub) {
//    interpreter.call(
//        callRequest {
//          // "{target}-{width}-{depth}-{max_sleep_millis}-{seed}"
//          key = "1-3-14-35000-abc"
//          commands = commands {
//            command.addAll(
//                listOf(
//                    command {
//                      asyncCall = asyncCall {
//                        target = 2 // won't deadlock
//                        callId = 1
//                        commands = commands {
//                          command.add(command { sleep = sleep { milliseconds = 35000 } })
//                        }
//                      }
//                    },
//                    command {
//                      asyncCall = asyncCall {
//                        target = 1 // would deadlock if awaited, but we don't await it
//                      }
//                    },
//                    command {
//                      asyncCallAwait = asyncCallAwait {
//                        callId = 1 // waits 5 seconds so we trigger the suspend timeout
//                      }
//                    }))
//          }
//        })
//  }
//
//  private fun testParams(): String {
//    var testSeed = System.getenv(E2E_VERIFICATION_SEED_ENV)
//    if (testSeed.isNullOrEmpty()) {
//      testSeed = generateAlphanumericString(16)
//    }
//    logger.info("Using seed {}", testSeed)
//
//    // "{width}-{depth}-{max_sleep_millis}-{seed}"
//    return "3-14-${5.seconds.inWholeMilliseconds.toInt()}-$testSeed"
//  }
//}
