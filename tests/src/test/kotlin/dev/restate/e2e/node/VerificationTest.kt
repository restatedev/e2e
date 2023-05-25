package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.TestParams
import dev.restate.e2e.services.verification.verifier.CommandVerifierGrpc.CommandVerifierBlockingStub
import dev.restate.e2e.services.verification.verifier.VerifierProto
import dev.restate.e2e.services.verification.verifier.VerifierProto.ExecuteRequest
import dev.restate.e2e.services.verification.verifier.VerifierProto.VerificationRequest
import dev.restate.e2e.utils.*
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.apache.logging.log4j.LogManager
import org.awaitility.kotlin.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class VerificationTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(Containers.VERIFICATION_FUNCTION_SPEC)
          .build()
    }

    private val ALPHANUMERIC_ALPHABET: Array<Char> =
        (('0'..'9').toList() + ('a'..'z').toList() + ('A'..'Z').toList()).toTypedArray()

    private val logger = LogManager.getLogger(VerificationTest::class.java)

    fun generateAlphanumericString(length: Int): String {
      return List(length) { Random.nextInt(0, ALPHANUMERIC_ALPHABET.size) }
          .map { ALPHANUMERIC_ALPHABET[it] }
          .joinToString(separator = "")
    }

    private val POLL_INTERVAL = 500.milliseconds.toJavaDuration()
    private val MAX_POLL_TIME = 1.minutes.toJavaDuration()

    fun CommandVerifierBlockingStub.awaitVerify(testParams: TestParams): Unit =
        await
            .pollInterval(POLL_INTERVAL)
            .atMost(MAX_POLL_TIME)
            .ignoreException(StatusRuntimeException::class)
            .untilAsserted {
              this.verify(VerificationRequest.newBuilder().setParams(testParams).build())
            }
  }

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @Test
  fun simple(@InjectBlockingStub verifier: CommandVerifierBlockingStub) {
    val testParams = testParams()

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())
    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @Test
  fun killingTheServiceEndpoint(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(Containers.VERIFICATION_FUNCTION_HOSTNAME)
      verificationContainer: ContainerHandle
  ) {
    val testParams = testParams()

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    verificationContainer.killAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @Test
  fun stoppingTheServiceEndpoint(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(Containers.VERIFICATION_FUNCTION_HOSTNAME)
      verificationContainer: ContainerHandle
  ) {
    val testParams = testParams()

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    verificationContainer.terminateAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @Test
  fun killingTheRuntime(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
  ) {
    val testParams = testParams()

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    runtimeContainer.killAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @Test
  fun stoppingTheRuntime(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
  ) {
    val testParams = testParams()

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    runtimeContainer.terminateAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  private fun testParams(): TestParams {
    val seed = generateAlphanumericString(16)
    // Some sample seeds helpful for debugging
    // W8JmvLJVlwAB0E2Z -> This should take ~3 seconds
    // idkQ4rJpKnpD60wC -> This should take ~10 seconds
    logger.info("Using seed {}", seed)
    return TestParams.newBuilder()
        .setSeed(seed)
        .setWidth(3)
        .setDepth(3)
        .setMaxSleepMillis(10.seconds.inWholeMilliseconds.toInt())
        .build()
  }
}
