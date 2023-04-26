package dev.restate.e2e.java

import com.google.protobuf.Empty
import dev.restate.e2e.Containers
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.functions.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.functions.counter.CounterProto.CounterRequest
import dev.restate.e2e.functions.counter.NoopGrpc.NoopBlockingStub
import dev.restate.e2e.functions.singletoncounter.SingletonCounterGrpc.SingletonCounterBlockingStub
import dev.restate.e2e.functions.singletoncounter.SingletonCounterProto.CounterNumber
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
class CounterTest {

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
  fun singleton(@InjectBlockingStub counterClient: SingletonCounterBlockingStub) {
    for (i in 1..10) {
      counterClient.add(CounterNumber.newBuilder().setValue(1).build())
    }

    assertThat(counterClient.get(Empty.getDefaultInstance()).value).isEqualTo(10)
  }

  @Test
  fun fireAndForget(
      @InjectBlockingStub noopClient: NoopBlockingStub,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())

    await untilCallTo
        {
          counterClient.get(
              CounterRequest.newBuilder().setCounterName("doAndReportInvocationCount").build())
        } matches
        { num ->
          num!!.value == 3L
        }
  }
}
