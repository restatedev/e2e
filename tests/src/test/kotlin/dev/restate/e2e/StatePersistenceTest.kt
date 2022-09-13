package dev.restate.e2e

import dev.restate.e2e.functions.counter.CounterAddRequest
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.utils.RestateDeployer
import io.grpc.ManagedChannel
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("requires-persistence-layer")
class StatePersistenceTest {

  private var deployer: RestateDeployer? = null
  private var channel: ManagedChannel? = null

  @BeforeEach
  fun before() {
    this.deployer = RestateDeployer.Builder().withFunction(Containers.COUNTER_FUNCTION_SPEC).build()
    deployer!!.deployAll(StatePersistenceTest::class.java)
    this.channel = deployer!!.getRuntimeChannel()
  }

  @AfterEach
  fun after() {
    channel!!.shutdownNow()
    channel!!.awaitTermination(10, TimeUnit.SECONDS)
    deployer!!.teardownAll()
  }

  @Test
  fun startAndStopRuntime() {
    var counterClient = CounterGrpc.newBlockingStub(channel)

    val res1 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Stop the runtime
    channel!!.shutdownNow()
    channel!!.awaitTermination(10, TimeUnit.SECONDS)
    deployer!!.teardownRuntime()

    // Start the runtime again
    deployer!!.deployRuntime(StatePersistenceTest::class.java)
    channel = deployer!!.getRuntimeChannel()
    counterClient =
        CounterGrpc.newBlockingStub(channel) // Port might have been changed when redeploying

    val res2 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }
}
