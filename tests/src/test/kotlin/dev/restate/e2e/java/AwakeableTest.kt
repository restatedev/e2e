package dev.restate.e2e.java

import dev.restate.e2e.Containers
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorGrpc.RandomNumberListGeneratorBlockingStub
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorProto.GenerateNumbersRequest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

// Need to implement the typescript e2e test:
// https://github.com/restatedev/e2e/issues/108
@Tag("always-suspending")
class AwakeableTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_EXTERNALCALL_SERVICE_SPEC)
                .withContainer(Containers.EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }

  @Test
  fun generate(
      @InjectBlockingStub randomNumberListGenerator: RandomNumberListGeneratorBlockingStub
  ) {
    assertThat(
            randomNumberListGenerator
                .generateNumbers(GenerateNumbersRequest.newBuilder().setItemsNumber(10).build())
                .numbersList)
        .isSorted.hasSize(10)
  }
}
