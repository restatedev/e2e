// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterProto
import dev.restate.e2e.services.errors.ErrorsProto
import dev.restate.e2e.services.errors.ErrorsProto.AttemptResponse
import dev.restate.e2e.services.errors.ErrorsProto.ErrorMessage
import dev.restate.e2e.services.errors.ErrorsProto.FailRequest
import dev.restate.e2e.services.errors.FailingServiceGrpc.FailingServiceBlockingStub
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.*
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.InstanceOfAssertFactories.type
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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_ERRORS_SERVICE_SPEC)
                .withServiceEndpoint(Containers.JAVA_EXTERNALCALL_SERVICE_SPEC)
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .withContainer(Containers.INT_SORTER_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }

  @DisplayName("Test propagate failure from sideEffect and internal invoke")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectFailurePropagation(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    assertThat(
            stub.invokeExternalAndHandleFailure(
                FailRequest.newBuilder().setKey(UUID.randomUUID().toString()).build()))
        .extracting(ErrorMessage::getErrorMessage, STRING)
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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_ERRORS_SERVICE_SPEC)
                .withServiceEndpoint(Containers.NODE_COUNTER_SERVICE_SPEC)
                .build())
  }

  @DisplayName("Test terminal error of failing side effect with finite retry policy is propagated")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun failingSideEffectWithFiniteRetryPolicy(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    val errorMessage = "some error message"

    assertThatThrownBy {
          stub.failingSideEffectWithFiniteRetryPolicy(
              ErrorMessage.newBuilder()
                  .setKey(UUID.randomUUID().toString())
                  .setErrorMessage(errorMessage)
                  .build())
        }
        .asInstanceOf(type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getDescription, InstanceOfAssertFactories.STRING)
        .contains("failing side effect action")
  }
}

private const val SUCCESS_ATTEMPT = 4

abstract class BaseErrorsTest {

  @DisplayName("Test calling method that fails terminally")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun invokeTerminallyFailingCall(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    val errorMessage = "my error"

    assertThatThrownBy {
          stub.terminallyFailingCall(
              ErrorMessage.newBuilder()
                  .setKey(UUID.randomUUID().toString())
                  .setErrorMessage(errorMessage)
                  .build())
        }
        .asInstanceOf(type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getDescription, InstanceOfAssertFactories.STRING)
        .contains(errorMessage)
  }

  @DisplayName("Test calling method that fails terminally multiple times")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun failSeveralTimes(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    // This test checks the endpoint doesn't become unstable after the first failure
    invokeTerminallyFailingCall(stub)
    invokeTerminallyFailingCall(stub)
    invokeTerminallyFailingCall(stub)
  }

  @DisplayName("Test set then fail should persist the set")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun setStateThenFailShouldPersistState(
      @InjectBlockingStub counterClient: CounterGrpc.CounterBlockingStub
  ) {
    val counterName = "my-failure-counter"

    assertThatThrownBy {
          counterClient.addThenFail(
              CounterProto.CounterAddRequest.newBuilder()
                  .setCounterName(counterName)
                  .setValue(1)
                  .build())
        }
        .asInstanceOf(type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getDescription, InstanceOfAssertFactories.STRING)
        .contains(counterName)

    val response =
        counterClient.get(
            CounterProto.CounterRequest.newBuilder().setCounterName(counterName).build())
    assertThat(response.value).isEqualTo(1)
  }

  @DisplayName("Test propagate failure from another service")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun internalCallFailurePropagation(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    val errorMessage = "propagated error"

    assertThatThrownBy {
          stub.callTerminallyFailingCall(
              ErrorMessage.newBuilder()
                  .setKey(UUID.randomUUID().toString())
                  .setErrorMessage(errorMessage)
                  .build())
        }
        .asInstanceOf(type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getDescription, InstanceOfAssertFactories.STRING)
        .contains(errorMessage)
  }

  @DisplayName("Test side effects are retried until they succeed")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectWithEventualSuccess(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    assertThat(
            stub.failingSideEffectWithEventualSuccess(
                ErrorsProto.Request.newBuilder().setKey(UUID.randomUUID().toString()).build()))
        .extracting(AttemptResponse::getAttempts, InstanceOfAssertFactories.INTEGER)
        .isEqualTo(SUCCESS_ATTEMPT)
  }

  @DisplayName("Test invocations are retried until they succeed")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun invocationWithEventualSuccess(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    assertThat(
            stub.failingCallWithEventualSuccess(
                ErrorsProto.Request.newBuilder().setKey(UUID.randomUUID().toString()).build()))
        .extracting(AttemptResponse::getAttempts, InstanceOfAssertFactories.INTEGER)
        .isEqualTo(SUCCESS_ATTEMPT)
  }

  @DisplayName("Test terminal error of side effects is propagated")
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun sideEffectWithTerminalError(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    val errorMessage = "failed side effect"

    assertThatThrownBy {
          stub.terminallyFailingSideEffect(
              ErrorMessage.newBuilder()
                  .setKey(UUID.randomUUID().toString())
                  .setErrorMessage(errorMessage)
                  .build())
        }
        .asInstanceOf(type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getDescription, InstanceOfAssertFactories.STRING)
        .contains(errorMessage)
  }
}
