package dev.restate.e2e.multi

import com.google.protobuf.Empty
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.functions.counter.CounterProto
import dev.restate.e2e.functions.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.functions.counter.NoopGrpc
import dev.restate.e2e.utils.InjectBlockingStub
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test

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
