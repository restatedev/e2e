package dev.restate.e2e.java

import com.google.protobuf.Empty
import dev.restate.e2e.Containers
import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.functions.counter.CounterProto.CounterRequest
import dev.restate.e2e.functions.counter.NoopGrpc.NoopBlockingStub
import dev.restate.e2e.functions.singletoncounter.SingletonCounterGrpc.SingletonCounterBlockingStub
import dev.restate.e2e.functions.singletoncounter.SingletonCounterProto.CounterNumber
import dev.restate.e2e.multi.BaseCounterTest
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
class CounterTest : BaseCounterTest() {

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
