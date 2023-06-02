package dev.restate.e2e.runtime

import com.google.protobuf.Empty
import dev.restate.e2e.Containers
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc.SingletonCounterBlockingStub
import dev.restate.e2e.services.singletoncounter.SingletonCounterProto.CounterNumber
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class SingletonCounterTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
                .build())
  }
  @Test
  fun singleton(@InjectBlockingStub counterClient: SingletonCounterBlockingStub) {
    for (i in 1..10) {
      counterClient.add(CounterNumber.newBuilder().setValue(1).build())
    }

    assertThat(counterClient.get(Empty.getDefaultInstance()).value).isEqualTo(10)
  }
}
