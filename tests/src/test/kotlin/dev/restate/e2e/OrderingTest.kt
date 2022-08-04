package dev.restate.e2e

import dev.restate.e2e.functions.collections.list.ListServiceGrpc.ListServiceBlockingStub
import dev.restate.e2e.functions.collections.list.Request
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.functions.coordinator.InvokeSequentiallyRequest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class OrderingTest {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withFunction(Containers.COORDINATOR_FUNCTION_SPEC)
                .withFunction(Containers.COLLECTIONS_FUNCTION_SPEC)
                .build())

    @JvmStatic
    fun ordering(): Stream<Arguments> {
      return Stream.of(
          Arguments.of(booleanArrayOf(true, false, true)),
          Arguments.of(booleanArrayOf(false, true, false)))
    }
  }

  @ParameterizedTest
  @MethodSource
  fun ordering(
      ordering: BooleanArray,
      @InjectBlockingStub coordinatorClient: CoordinatorBlockingStub,
      @InjectBlockingStub listClient: ListServiceBlockingStub
  ) {
    coordinatorClient.invokeSequentially(
        InvokeSequentiallyRequest.newBuilder()
            .addAllExecuteAsBackgroundCall(ordering.asIterable())
            .build())

    val listClientRequest = Request.newBuilder().setListName("invokeSequentially").build()

    assertThat(listClient.clear(listClientRequest).valuesList).containsExactly("0", "1", "2")
  }
}
