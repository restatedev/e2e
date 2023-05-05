package dev.restate.e2e

import dev.restate.e2e.functions.verification.interpreter.InterpreterProto.TestParams
import dev.restate.e2e.functions.verification.verifier.CommandVerifierGrpc.CommandVerifierBlockingStub
import dev.restate.e2e.functions.verification.verifier.VerifierProto
import dev.restate.e2e.functions.verification.verifier.VerifierProto.ExecuteRequest
import dev.restate.e2e.functions.verification.verifier.VerifierProto.VerificationRequest
import dev.restate.e2e.utils.*
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.awaitility.kotlin.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

// We need https://github.com/restatedev/sdk-typescript/pull/9 for this
// @Tag("always-suspending")
@Disabled(
    "Without https://github.com/restatedev/restate-verification/issues/7 it's hard to predict time, and there's always a chance these tests can fail")
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

    fun generateAlphanumericString(length: Int): String {
      return List(length) { Random.nextInt(0, ALPHANUMERIC_ALPHABET.size) }
          .map { ALPHANUMERIC_ALPHABET[it] }
          .joinToString(separator = "")
    }

    private val POLL_INTERVAL = 1.seconds.toJavaDuration()
    private val MAX_POLL_TIME = 10.minutes.toJavaDuration()

    fun CommandVerifierBlockingStub.awaitVerify(testParams: TestParams): Unit =
        await
            .pollInterval(POLL_INTERVAL)
            .atMost(MAX_POLL_TIME)
            .ignoreException(StatusRuntimeException::class)
            .untilAsserted {
              this.verify(VerificationRequest.newBuilder().setParams(testParams).build())
            }
  }

  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  @Test
  fun simple(@InjectBlockingStub verifier: CommandVerifierBlockingStub) {
    val testParams = testParams(16, 10, 4)

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())
    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  @Test
  fun killingTheServiceEndpoint(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(Containers.VERIFICATION_FUNCTION_HOSTNAME)
      verificationContainer: ContainerHandle
  ) {
    val testParams = testParams(16, 10, 4)

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    verificationContainer.killAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  @Test
  fun stoppingTheServiceEndpoint(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(Containers.VERIFICATION_FUNCTION_HOSTNAME)
      verificationContainer: ContainerHandle
  ) {
    val testParams = testParams(16, 10, 4)

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    verificationContainer.terminateAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  @Test
  fun killingTheRuntime(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
  ) {
    val testParams = testParams(16, 10, 4)

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    runtimeContainer.killAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  @Test
  fun stoppingTheRuntime(
      @InjectBlockingStub verifier: CommandVerifierBlockingStub,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
  ) {
    val testParams = testParams(16, 10, 4)

    verifier.execute(ExecuteRequest.newBuilder().setParams(testParams).build())

    Thread.sleep(Random.nextInt(1..10).seconds.inWholeMilliseconds)

    runtimeContainer.terminateAndRestart()

    verifier.awaitVerify(testParams)

    verifier.clear(VerifierProto.ClearRequest.newBuilder().setParams(testParams).build())
  }

  private fun testParams(seedLength: Int, width: Int, depth: Int): TestParams {
    return TestParams.newBuilder()
        .setSeed(generateAlphanumericString(seedLength))
        .setWidth(width)
        .setDepth(depth)
        .build()
  }
}
