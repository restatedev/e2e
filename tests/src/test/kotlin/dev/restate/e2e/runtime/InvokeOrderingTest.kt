// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.e2e.Containers
import dev.restate.e2e.services.collections.list.ListProto.Request
import dev.restate.e2e.services.collections.list.ListServiceGrpc.ListServiceBlockingStub
import dev.restate.e2e.services.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.services.coordinator.CoordinatorProto.InvokeSequentiallyRequest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.util.UUID
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** Test the ordering is respected between invoke and background invoke */
class InvokeOrderingTest {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_SERVICE_SPEC)
                .withServiceEndpoint(Containers.JAVA_COLLECTIONS_SERVICE_SPEC)
                .build())

    @JvmStatic
    fun ordering(): Stream<Arguments> {
      // To enforce ordering wrt listClient.clear(...) executed in the test code,
      // the last call must be sync!
      return Stream.of(
          Arguments.of(booleanArrayOf(true, false, false)),
          Arguments.of(booleanArrayOf(false, true, false)),
          Arguments.of(
              booleanArrayOf(true, true, false),
          ))
    }
  }

  @ParameterizedTest
  @MethodSource
  @Execution(ExecutionMode.CONCURRENT)
  fun ordering(
      ordering: BooleanArray,
      @InjectBlockingStub coordinatorClient: CoordinatorBlockingStub,
      @InjectBlockingStub listClient: ListServiceBlockingStub
  ) {
    val listName = UUID.randomUUID().toString()

    coordinatorClient.invokeSequentially(
        InvokeSequentiallyRequest.newBuilder()
            .addAllExecuteAsBackgroundCall(ordering.asIterable())
            .setListName(listName)
            .build())

    val listClientRequest = Request.newBuilder().setListName(listName).build()

    assertThat(listClient.clear(listClientRequest).valuesList).containsExactly("0", "1", "2")
  }
}
