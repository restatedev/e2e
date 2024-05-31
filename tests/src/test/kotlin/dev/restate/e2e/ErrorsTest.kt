// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.InjectClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.Client
import java.util.*
import my.restate.e2e.services.CounterClient
import my.restate.e2e.services.FailingClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
class JavaErrorsTest : BaseErrorsTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_ERRORS_SERVICE_SPEC)
                .withServiceEndpoint(Containers.JAVA_EXTERNALCALL_SERVICE_SPEC)
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .withContainer(Containers.INT_SORTER_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }

  @DisplayName("Test propagate failure from sideEffect and internal invoke")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectFailurePropagation(@InjectClient ingressClient: Client) {
    assertThat(
            FailingClient.fromClient(ingressClient, UUID.randomUUID().toString())
                .invokeExternalAndHandleFailure())
        // We match on this regex because there might be additional parts of the string injected
        // by runtime/sdk in the error message strings
        .matches("begin.*external_call.*internal_call")
  }
}

class NodeErrorsTest : BaseErrorsTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.NODE_ERRORS_SERVICE_SPEC)
                .withServiceEndpoint(Containers.NODE_COUNTER_SERVICE_SPEC)
                .build())
  }

  //  @DisplayName("Test terminal error of failing side effect with finite retry policy is
  // propagated")
  //  @Test
  //  @Execution(ExecutionMode.CONCURRENT)
  //  fun failingSideEffectWithFiniteRetryPolicy(@InjectBlockingStub stub:
  // FailingServiceBlockingStub) {
  //    val errorMessage = "some error message"
  //
  //    assertThatThrownBy {
  //          stub.failingSideEffectWithFiniteRetryPolicy(
  //              ErrorMessage.newBuilder()
  //                  .setKey(UUID.randomUUID().toString())
  //                  .setErrorMessage(errorMessage)
  //                  .build())
  //        }
  //        .asInstanceOf(type(StatusRuntimeException::class.java))
  //        .extracting(StatusRuntimeException::getStatus)
  //        .extracting(Status::getDescription, InstanceOfAssertFactories.STRING)
  //        .contains("failing side effect action")
  //  }
}

private const val SUCCESS_ATTEMPT = 4

abstract class BaseErrorsTest {

  @DisplayName("Test calling method that fails terminally")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun invokeTerminallyFailingCall(@InjectClient ingressClient: Client) {
    val errorMessage = "my error"

    assertThatThrownBy {
          FailingClient.fromClient(ingressClient, UUID.randomUUID().toString())
              .terminallyFailingCall(errorMessage)
        }
        .hasMessageContaining(errorMessage)
  }

  @DisplayName("Test calling method that fails terminally multiple times")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun failSeveralTimes(@InjectClient ingressClient: Client) {
    // This test checks the endpoint doesn't become unstable after the first failure
    invokeTerminallyFailingCall(ingressClient)
    invokeTerminallyFailingCall(ingressClient)
    invokeTerminallyFailingCall(ingressClient)
  }

  @DisplayName("Test set then fail should persist the set")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun setStateThenFailShouldPersistState(@InjectClient ingressClient: Client) {
    val counterName = "my-failure-counter"
    val counterClient = CounterClient.fromClient(ingressClient, counterName)

    assertThatThrownBy { counterClient.addThenFail(1) }.hasMessageContaining(counterName)

    assertThat(counterClient.get()).isEqualTo(1)
  }

  @DisplayName("Test propagate failure from another service")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun internalCallFailurePropagation(@InjectClient ingressClient: Client) {
    val errorMessage = "propagated error"
    val failingClient = FailingClient.fromClient(ingressClient, UUID.randomUUID().toString())

    assertThatThrownBy { failingClient.callTerminallyFailingCall(errorMessage) }
        .hasMessageContaining(errorMessage)
  }

  @DisplayName("Test side effects are retried until they succeed")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectWithEventualSuccess(@InjectClient ingressClient: Client) {
    assertThat(
            FailingClient.fromClient(ingressClient, UUID.randomUUID().toString())
                .failingCallWithEventualSuccess())
        .isEqualTo(SUCCESS_ATTEMPT)
  }

  @DisplayName("Test invocations are retried until they succeed")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun invocationWithEventualSuccess(@InjectClient ingressClient: Client) {
    assertThat(
            FailingClient.fromClient(ingressClient, UUID.randomUUID().toString())
                .failingCallWithEventualSuccess())
        .isEqualTo(SUCCESS_ATTEMPT)
  }

  @DisplayName("Test terminal error of side effects is propagated")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectWithTerminalError(@InjectClient ingressClient: Client) {
    val errorMessage = "failed side effect"

    assertThatThrownBy {
          FailingClient.fromClient(ingressClient, UUID.randomUUID().toString())
              .terminallyFailingSideEffect(errorMessage)
        }
        .hasMessageContaining(errorMessage)
  }
}
