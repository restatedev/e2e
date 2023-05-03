package dev.restate.e2e.java

import dev.restate.e2e.Containers
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension

class StatePersistenceTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(Containers.JAVA_COUNTER_FUNCTION_SPEC)
          .build()
    }
  }

  @Test
  fun startAndStopRuntime(
      @InjectBlockingStub counterClient: CounterGrpc.CounterBlockingStub,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeHandle: ContainerHandle
  ) {
    val res1 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Stop and start the runtime
    runtimeHandle.terminateAndRestart()

    val res2 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Test
  fun startAndKillRuntime(
      @InjectBlockingStub counterClient: CounterGrpc.CounterBlockingStub,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeHandle: ContainerHandle
  ) {
    val res1 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Stop and start the runtime
    runtimeHandle.killAndRestart()

    val res2 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }
}
