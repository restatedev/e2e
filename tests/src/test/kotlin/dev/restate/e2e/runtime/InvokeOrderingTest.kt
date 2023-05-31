package dev.restate.e2e.runtime

import dev.restate.e2e.Containers
import dev.restate.e2e.functions.collections.list.ListProto.Request
import dev.restate.e2e.functions.collections.list.ListServiceGrpc.ListServiceBlockingStub
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc.CoordinatorBlockingStub
import dev.restate.e2e.functions.coordinator.CoordinatorProto.InvokeSequentiallyRequest
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
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
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_FUNCTION_SPEC)
                .withServiceEndpoint(Containers.JAVA_COLLECTIONS_FUNCTION_SPEC)
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
