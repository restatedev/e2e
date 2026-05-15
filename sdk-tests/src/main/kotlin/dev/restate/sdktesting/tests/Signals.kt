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
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitOne
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.CreateSignal
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.InterpretRequest
import dev.restate.sdktesting.infra.*
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

class Signals {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(
          ServiceSpec.defaultBuilder()
              .withServices(VirtualObjectCommandInterpreter::class, TestUtilsService::class))
    }
  }

  @Test
  @DisplayName("Await one signal with successful resolution")
  @Execution(ExecutionMode.CONCURRENT)
  fun resolve(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(InterpretRequest(listOf(AwaitOne(CreateSignal("mysignal")))))
            }
            .options(idempotentCallOptions)
            .send()

    ingressClient
        .toService<TestUtilsService>()
        .request {
          resolveSignal(ResolveSignalRequest(sendResponse.invocationId(), "mysignal", "hello"))
        }
        .options(idempotentCallOptions)
        .call()

    assertThat(sendResponse.attachSuspend().response).isEqualTo("hello")
  }

  @Test
  @DisplayName("Await one signal with rejection propagates as terminal error")
  @Execution(ExecutionMode.CONCURRENT)
  fun reject(@InjectClient ingressClient: Client) = runTest {
    val testId = UUID.randomUUID().toString()
    val interpreterClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(testId)

    val sendResponse =
        interpreterClient
            .request {
              interpretCommands(InterpretRequest(listOf(AwaitOne(CreateSignal("mysignal")))))
            }
            .options(idempotentCallOptions)
            .send()

    ingressClient
        .toService<TestUtilsService>()
        .request {
          rejectSignal(RejectSignalRequest(sendResponse.invocationId(), "mysignal", "boom"))
        }
        .options(idempotentCallOptions)
        .call()

    assertThat(runCatching { sendResponse.attachSuspend().response }.exceptionOrNull())
        .message()
        .contains("boom")
  }
}
