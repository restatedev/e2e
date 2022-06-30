package dev.restate.e2e

import dev.restate.e2e.functions.externalcall.GenerateNumbersRequest
import dev.restate.e2e.functions.externalcall.RandomNumberListGeneratorGrpc.RandomNumberListGeneratorBlockingStub
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ExternalCallTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withFunction(Containers.EXTERNALCALL_FUNCTION_SPEC)
                .withContainer(Containers.EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }

  @Test
  fun generate(
      @InjectBlockingStub(
          // TODO The reason for setting the key here is to avoid a deadlock, which is caused by the
          // partition processor blocking on the http request to the function service. Once the
          // partition processor will be able to process asynchronously the responses from the
          // functions, there should be no deadlock anymore and we must remove this key.
          // https://github.com/restatedev/runtime/issues/134
          "abc")
      randomNumberListGenerator: RandomNumberListGeneratorBlockingStub
  ) {
    assertThat(
            randomNumberListGenerator
                .generateNumbers(GenerateNumbersRequest.newBuilder().setItemsNumber(10).build())
                .numbersList)
        .isSorted.hasSize(10)
  }
}
