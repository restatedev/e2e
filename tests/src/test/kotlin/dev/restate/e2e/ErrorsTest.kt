package dev.restate.e2e

import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.CounterProto
import dev.restate.e2e.functions.errors.ErrorsProto.ErrorMessage
import dev.restate.e2e.functions.errors.ErrorsProto.FailRequest
import dev.restate.e2e.functions.errors.FailingServiceGrpc.FailingServiceBlockingStub
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaErrorsTest : BaseErrorsTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_ERRORS_FUNCTION_SPEC)
                .withServiceEndpoint(Containers.JAVA_EXTERNALCALL_FUNCTION_SPEC)
                .withServiceEndpoint(Containers.JAVA_COUNTER_FUNCTION_SPEC)
                .withContainer(Containers.EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }

  @Test
  fun externalCallFailurePropagation(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    assertThat(
            stub.invokeExternalAndHandleFailure(
                FailRequest.newBuilder().setKey(UUID.randomUUID().toString()).build()))
        .extracting(ErrorMessage::getErrorMessage)
        .isEqualTo("begin:external_call:internal_call")
  }

  @Test
  fun propagate404(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    assertThat(
            stub.handleNotFound(
                FailRequest.newBuilder().setKey(UUID.randomUUID().toString()).build()))
        .extracting(ErrorMessage::getErrorMessage)
        .isEqualTo("notfound")
  }
}

class NodeErrorsTest : BaseErrorsTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_ERRORS_FUNCTION_SPEC)
                .withServiceEndpoint(Containers.NODE_COUNTER_FUNCTION_SPEC)
                .build())
  }
}

abstract class BaseErrorsTest {

  @Test
  fun fail(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    val errorMessage = "my error"

    assertThatThrownBy {
          stub.fail(
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

  @Test
  fun failSeveralTimes(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    // This test checks the endpoint doesn't become unstable after the first failure
    fail(stub)
    fail(stub)
    fail(stub)
  }

  @Test
  fun addThenFail(@InjectBlockingStub counterClient: CounterGrpc.CounterBlockingStub) {
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

  @Test
  fun internalCallFailurePropagation(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    val errorMessage = "propagated error"

    assertThat(
            stub.failAndHandle(
                ErrorMessage.newBuilder()
                    .setKey(UUID.randomUUID().toString())
                    .setErrorMessage(errorMessage)
                    .build()))
        .extracting(ErrorMessage::getErrorMessage, InstanceOfAssertFactories.STRING)
        .contains(errorMessage)
  }
}
