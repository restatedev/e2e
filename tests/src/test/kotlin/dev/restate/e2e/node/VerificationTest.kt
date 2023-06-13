package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.services.verification.interpreter.CommandInterpreterGrpc.CommandInterpreterBlockingStub
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.CallRequest
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.Command
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.Command.AsyncCall
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.Command.AsyncCallAwait
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.Command.Sleep
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.Commands
import dev.restate.e2e.services.verification.interpreter.InterpreterProto.Key
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
import org.junit.jupiter.api.DisplayName
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
          .withServiceEndpoint(Containers.VERIFICATION_SERVICE_SPEC)
          .build()
    }

    private const val E2E_VERIFICATION_SEED_ENV = "E2E_VERIFICATION_SEED"

    private val ALPHANUMERIC_ALPHABET: Array<Char> =
        (('0'..'9').toList() + ('a'..'z').toList() + ('A'..'Z').toList()).toTypedArray()

    private val logger = LogManager.getLogger(VerificationTest::class.java)

    fun generateAlphanumericString(length: Int): String {
      return List(length) { Random.nextInt(0, ALPHANUMERIC_ALPHABET.size) }
          .map { ALPHANUMERIC_ALPHABET[it] }
          .joinToString(separator = "")
    }

    private val POLL_INTERVAL = 500.milliseconds.toJavaDuration()
    private val MAX_POLL_TIME = 2.minutes.toJavaDuration()

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
      @InjectContainerHandle(Containers.VERIFICATION_SERVICE_HOSTNAME)
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
      @InjectContainerHandle(Containers.VERIFICATION_SERVICE_HOSTNAME)
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

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @DisplayName("Suspending or returning with an unawaited blocked syncCall should not deadlock")
  @Test
  fun unawaitedSelfCall(@InjectBlockingStub interpreter: CommandInterpreterBlockingStub) {
    val params = TestParams.newBuilder().build()
    val key = Key.newBuilder().setParams(params).setTarget(1).build()
    val commands =
        Commands.newBuilder()
            .addCommand(
                Command.newBuilder()
                    .setAsyncCall(
                        AsyncCall.newBuilder()
                            .setTarget(2) // won't deadlock
                            .setCallId(1)
                            .setCommands(
                                Commands.newBuilder()
                                    .addCommand(
                                        Command.newBuilder()
                                            .setSleep(Sleep.newBuilder().setMilliseconds(5000)))
                                    .build())
                            .build())
                    .build())
            .addCommand(
                Command.newBuilder()
                    .setAsyncCall(
                        AsyncCall.newBuilder()
                            .setTarget(1) // would deadlock if awaited, but we don't await it
                            .build())
                    .build())
            .addCommand(
                Command.newBuilder()
                    .setAsyncCallAwait(
                        AsyncCallAwait.newBuilder()
                            .setCallId(1) // waits 5 seconds so we trigger the suspend timeout
                            .build()))
            .build()
    interpreter.call(CallRequest.newBuilder().setKey(key).setCommands(commands).build())
  }

  private fun testParams(): TestParams {
    var seed = System.getenv(E2E_VERIFICATION_SEED_ENV)
    if (seed.isNullOrEmpty()) {
      seed = generateAlphanumericString(16)
    }

    logger.info("Using seed {}", seed)
    return TestParams.newBuilder()
        .setSeed(seed)
        .setWidth(3)
        .setDepth(14)
        .setMaxSleepMillis(5.seconds.inWholeMilliseconds.toInt())
        .build()
  }
}
