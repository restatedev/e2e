package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.CounterProto
import dev.restate.e2e.services.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.services.counter.NoopGrpc
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaStateTest : BaseStateTest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_FUNCTION_SPEC)
                .build())
  }
}

@Tag("always-suspending")
class NodeStateTest : BaseStateTest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_COUNTER_FUNCTION_SPEC)
                .build())
  }
}

abstract class BaseStateTest {

  @Test
  fun add(@InjectBlockingStub counterClient: CounterBlockingStub) {
    counterClient.add(
        CounterAddRequest.newBuilder().setCounterName("noReturnValue").setValue(1).build())
  }

  @Test
  fun getAndSet(@InjectBlockingStub counterClient: CounterBlockingStub) {
    val res1 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    val res2 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Test
  fun setStateViaOneWayCallFromAnotherService(
      @InjectBlockingStub noopClient: NoopGrpc.NoopBlockingStub,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())

    await untilCallTo
        {
          counterClient.get(
              CounterProto.CounterRequest.newBuilder()
                  .setCounterName("doAndReportInvocationCount")
                  .build())
        } matches
        { num ->
          num!!.value == 3L
        }
  }
}
