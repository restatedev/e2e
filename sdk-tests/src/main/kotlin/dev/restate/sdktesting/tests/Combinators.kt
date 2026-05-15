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
import dev.restate.client.kotlin.*
import dev.restate.sdktesting.contracts.TestUtilsService
import dev.restate.sdktesting.contracts.TestUtilsService.RejectSignalRequest
import dev.restate.sdktesting.contracts.TestUtilsService.ResolveSignalRequest
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAllCompleted
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAllSucceededOrFirstFailed
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAny
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAnySuccessful
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAwakeableOrTimeout
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitFirstCompleted
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitFirstSucceededOrAllFailed
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.CreateAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.CreateSignal
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.InterpretRequest
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.RejectAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.ResolveAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.RunReturns
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.RunThrowTerminalException
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.Sleep
import dev.restate.sdktesting.infra.*
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.async
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

class Combinators {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(
          ServiceSpec.defaultBuilder()
              .withServices(VirtualObjectCommandInterpreter::class, TestUtilsService::class))
    }
  }

  @Test
  @DisplayName("Test awakeable or timeout using Await any")
  @Execution(ExecutionMode.CONCURRENT)
  fun awakeableOrTimeoutUsingAwaitAny(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val timeout = Duration.ofMillis(100L)

    assertThat(
            ingressClient
                .toVirtualObject<VirtualObjectCommandInterpreter>(testId)
                .request {
                  interpretCommands(
                      InterpretRequest(
                          listOf(
                              AwaitAny(
                                  listOf(CreateAwakeable("awk1"), Sleep(timeout.toMillis()))))))
                }
                .options(idempotentCallOptions)
                .call()
                .response)
        .isEqualTo("sleep")
  }

  @Test
  @DisplayName("Test awakeable or timeout using AwaitAwakeableOrTimeout")
  @Execution(ExecutionMode.CONCURRENT)
  fun awakeableOrTimeoutUsingAwakeableTimeoutCommand(@InjectClient ingressClient: Client) =
      runTest {
        val testId = UUID.randomUUID().toString()
        val timeout = Duration.ofMillis(100L)

        assertThat(
                runCatching {
                      ingressClient
                          .toVirtualObject<VirtualObjectCommandInterpreter>(testId)
                          .request {
                            interpretCommands(
                                InterpretRequest(
                                    listOf(
                                        AwaitAwakeableOrTimeout(
                                            "should-timeout-awk", timeout.toMillis()))))
                          }
                          .options(idempotentCallOptions)
                          .call()
                          .response
                    }
                    .exceptionOrNull())
            .message()
            .contains("await-timeout")
      }

  @Test
  @DisplayName("Test the first successful awakeable should be returned")
  @Execution(ExecutionMode.CONCURRENT)
  fun firstSuccessfulCompletedAwakeable(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val awk0 = "awk0"
    val awk1 = "awk1"
    val awk2 = "awk2"

    val result = async {
      interpreterClient
          .request {
            interpretCommands(
                InterpretRequest(
                    listOf(
                        AwaitAnySuccessful(
                            listOf(
                                CreateAwakeable(awk0),
                                RunThrowTerminalException("run0"),
                                CreateAwakeable(awk1),
                                RunThrowTerminalException("run1"),
                                CreateAwakeable(awk2),
                                RunThrowTerminalException("run2"),
                            )))))
          }
          .options(idempotentCallOptions)
          .call()
          .response
    }

    await withAlias
        "awakeable $awk2 created" untilAsserted
        {
          assertThat(interpreterClient.request { hasAwakeable(awk2) }.call().response).isTrue()
        }

    // hasAwakeable might have to be retried in case of leadership changes
    assertThat(
            interpreterClient
                .request { hasAwakeable(awk0) }
                .options(idempotentCallOptions)
                .call()
                .response)
        .isTrue()
    // hasAwakeable might have to be retried in case of leadership changes
    assertThat(
            interpreterClient
                .request { hasAwakeable(awk1) }
                .options(idempotentCallOptions)
                .call()
                .response)
        .isTrue()

    // Now let's reject awakeable 2, this should not complete anything
    interpreterClient
        .request { rejectAwakeable(RejectAwakeable(awk2, "fail")) }
        .options(idempotentCallOptions)
        .call()

    // Resolve awakeable 1, this will complete successfully
    interpreterClient
        .request { resolveAwakeable(ResolveAwakeable(awk1, "awk1-result")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(result.await()).isEqualTo("awk1-result")
  }

  @Test
  @DisplayName("Signals: await first successful or all failed")
  @Execution(ExecutionMode.CONCURRENT)
  fun signalFirstSuccessfulOrAllFailed(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitAnySuccessful(
                              listOf(
                                  CreateSignal("sig0"),
                                  CreateSignal("sig1"),
                                  CreateSignal("sig2"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { rejectSignal(RejectSignalRequest(invocationId, "sig0", "fail0")) }
        .options(idempotentCallOptions)
        .call()
    utilsClient
        .request { rejectSignal(RejectSignalRequest(invocationId, "sig2", "fail2")) }
        .options(idempotentCallOptions)
        .call()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig1", "success1")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("success1")
  }

  @Test
  @DisplayName("Signals: await all successful or first failed")
  @Execution(ExecutionMode.CONCURRENT)
  fun signalAllSuccessfulOrFirstFailed(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitAllSucceededOrFirstFailed(
                              listOf(
                                  CreateSignal("sig0"),
                                  CreateSignal("sig1"),
                                  CreateSignal("sig2"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig0", "val0")) }
        .options(idempotentCallOptions)
        .call()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig1", "val1")) }
        .options(idempotentCallOptions)
        .call()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig2", "val2")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("val0|val1|val2")
  }

  @Test
  @DisplayName("Signals: await all completed")
  @Execution(ExecutionMode.CONCURRENT)
  fun signalAllCompleted(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitAllCompleted(
                              listOf(
                                  CreateSignal("sig0"),
                                  CreateSignal("sig1"),
                                  CreateSignal("sig2"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig0", "val0")) }
        .options(idempotentCallOptions)
        .call()
    utilsClient
        .request { rejectSignal(RejectSignalRequest(invocationId, "sig1", "err1")) }
        .options(idempotentCallOptions)
        .call()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig2", "val2")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("ok:val0|err:err1|ok:val2")
  }

  @Test
  @DisplayName("Signals: await first completed")
  @Execution(ExecutionMode.CONCURRENT)
  fun signalFirstCompleted(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitAny(
                              listOf(
                                  CreateSignal("sig0"),
                                  CreateSignal("sig1"),
                                  CreateSignal("sig2"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig1", "first")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("first")
  }

  @Test
  @DisplayName("Mixed: run wins the race against an unresolved signal")
  @Execution(ExecutionMode.CONCURRENT)
  fun mixedRunWinsRaceAgainstSignal(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()

    // RunReturns completes immediately; the signal is never resolved so it can't win.
    assertThat(
            ingressClient
                .toVirtualObject<VirtualObjectCommandInterpreter>(testId)
                .request {
                  interpretCommands(
                      InterpretRequest(
                          listOf(
                              AwaitFirstCompleted(
                                  listOf(CreateSignal("sig"), RunReturns("runval"))))))
                }
                .options(idempotentCallOptions)
                .call()
                .response)
        .isEqualTo("runval")
  }

  @Test
  @DisplayName("Mixed: failed run is skipped, signal succeeds in AwaitFirstSucceededOrAllFailed")
  @Execution(ExecutionMode.CONCURRENT)
  fun mixedSignalWinsAfterRunFails(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitFirstSucceededOrAllFailed(
                              listOf(RunThrowTerminalException("fail"), CreateSignal("sig"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig", "sigval")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("sigval")
  }

  @Test
  @DisplayName("Mixed: AwaitAllSucceededOrFirstFailed with run and signal both succeed")
  @Execution(ExecutionMode.CONCURRENT)
  fun mixedAllSucceededRunAndSignal(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitAllSucceededOrFirstFailed(
                              listOf(RunReturns("runval"), CreateSignal("sig"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig", "sigval")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("runval|sigval")
  }

  @Test
  @DisplayName("Mixed: AwaitAllCompleted with run (ok), signal (ok), and failed run (err)")
  @Execution(ExecutionMode.CONCURRENT)
  fun mixedAllCompletedRunSignalAndFailedRun(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)
    val utilsClient = ingressClient.toService<TestUtilsService>()

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(
                  InterpretRequest(
                      listOf(
                          AwaitAllCompleted(
                              listOf(
                                  RunReturns("runval"),
                                  CreateSignal("sig"),
                                  RunThrowTerminalException("fail"))))))
            }
            .options(idempotentCallOptions)
            .send()

    val invocationId = sendResponse.invocationId()
    utilsClient
        .request { resolveSignal(ResolveSignalRequest(invocationId, "sig", "sigval")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("ok:runval|ok:sigval|err:fail")
  }
}
