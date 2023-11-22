// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorGrpc.RandomNumberListGeneratorBlockingStub
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorProto.GenerateNumbersRequest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaAwakeableTest : BaseAwakeableTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_EXTERNALCALL_SERVICE_SPEC)
                .withContainer(Containers.INT_SORTER_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class NodeAwakeableTest : BaseAwakeableTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_EXTERNALCALL_SERVICE_SPEC)
                .withContainer(Containers.INT_SORTER_HTTP_SERVER_CONTAINER_SPEC)
                .build())
  }
}

class NodeWithBase64CompletionAwakeableTest : BaseAwakeableTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_EXTERNALCALL_SERVICE_SPEC)
                .withContainer(
                    Containers.INT_SORTER_HTTP_SERVER_HOSTNAME to
                        Containers.intSorterHttpServerContainer()
                            .withEnv("ENCODE_RESULT_AS_BASE64", "true"))
                .build())
  }
}

abstract class BaseAwakeableTest {

  @Test
  fun generate(
      @InjectBlockingStub randomNumberListGenerator: RandomNumberListGeneratorBlockingStub
  ) {
    assertThat(
            randomNumberListGenerator
                .generateNumbers(GenerateNumbersRequest.newBuilder().setItemsNumber(10).build())
                .numbersList)
        .isSorted
        .hasSize(10)
  }
}
