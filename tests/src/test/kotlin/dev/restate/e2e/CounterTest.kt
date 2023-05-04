package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.functions.counter.CounterProto
import dev.restate.e2e.functions.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.functions.counter.NoopGrpc
import dev.restate.e2e.functions.singletoncounter.SingletonCounterGrpc
import dev.restate.e2e.functions.singletoncounter.SingletonCounterProto
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
class JavaCounterTest : BaseCounterTest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_FUNCTION_SPEC)
                .build())
  }
  @Test
  fun singleton(
      @InjectBlockingStub counterClient: SingletonCounterGrpc.SingletonCounterBlockingStub
  ) {
    for (i in 1..10) {
      counterClient.add(SingletonCounterProto.CounterNumber.newBuilder().setValue(1).build())
    }

    assertThat(counterClient.get(Empty.getDefaultInstance()).value).isEqualTo(10)
  }
}

// We need https://github.com/restatedev/sdk-typescript/pull/9 for this
// @Tag("always-suspending")
class NodeCounterTest : BaseCounterTest() {

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

abstract class BaseCounterTest {

  @Test
  fun noReturnValue(@InjectBlockingStub counterClient: CounterBlockingStub) {
    counterClient.add(
        CounterAddRequest.newBuilder().setCounterName("noReturnValue").setValue(1).build())
  }

  @Test
  fun keyedState(@InjectBlockingStub counterClient: CounterBlockingStub) {
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
  fun fireAndForget(
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
