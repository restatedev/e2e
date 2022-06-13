package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.functions.counter.NoopGrpc.NoopBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.e2e.utils.RestateDeployerExtension.InjectBlockingStub
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CounterTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder().functionSpec(Containers.COUNTER_FUNCTION_SPEC).build())
  }

  @Test
  fun noReturnValue(@InjectBlockingStub("e2e-counter") counterClient: CounterBlockingStub) {
    counterClient.add(dev.restate.e2e.functions.counter.Number.newBuilder().setValue(1).build())
  }

  @Test
  fun keyedState(@InjectBlockingStub("e2e-counter", "my-key") counterClient: CounterBlockingStub) {
    val res1 =
        counterClient.getAndAdd(
            dev.restate.e2e.functions.counter.Number.newBuilder().setValue(1).build())
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    val res2 =
        counterClient.getAndAdd(
            dev.restate.e2e.functions.counter.Number.newBuilder().setValue(2).build())
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Test
  fun fireAndForget(
      @InjectBlockingStub("e2e-counter") noopClient: NoopBlockingStub,
      @InjectBlockingStub("e2e-counter", "doAndReportInvocationCount")
      counterClient: CounterBlockingStub
  ) {
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())
    noopClient.doAndReportInvocationCount(Empty.getDefaultInstance())

    await untilCallTo
        {
          counterClient.get(Empty.getDefaultInstance())
        } matches
        { num ->
          num!!.value == 3L
        }
  }
}
