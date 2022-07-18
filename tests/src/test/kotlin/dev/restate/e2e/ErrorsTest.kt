package dev.restate.e2e

import dev.restate.e2e.functions.errors.ErrorMessage
import dev.restate.e2e.functions.errors.FailRequest
import dev.restate.e2e.functions.errors.FailingServiceGrpc.FailingServiceBlockingStub
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ErrorsTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withFunction(Containers.ERRORS_FUNCTION_SPEC)
                .withFunction(Containers.EXTERNALCALL_FUNCTION_SPEC)
                .withContainer(Containers.EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }

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
        .extracting(Status::getDescription)
        .isEqualTo(errorMessage)
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
        .extracting(ErrorMessage::getErrorMessage)
        .isEqualTo(errorMessage)
  }

  @Test
  fun externalCallFailurePropagation(@InjectBlockingStub stub: FailingServiceBlockingStub) {
    assertThat(
            stub.invokeExternalAndHandleFailure(
                FailRequest.newBuilder().setKey(UUID.randomUUID().toString()).build()))
        .extracting(ErrorMessage::getErrorMessage)
        .isEqualTo("begin:external_call:internal_call")
  }
}
