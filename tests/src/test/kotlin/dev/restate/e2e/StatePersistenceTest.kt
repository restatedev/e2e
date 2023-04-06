package dev.restate.e2e

import dev.restate.e2e.GrpcUtils.blockingUseAndTerminate
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.utils.RestateDeployer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@Disabled("Persistence is not supported yet")
@Tag("requires-persistence-layer")
class StatePersistenceTest {

  private val reportDir: String =
      RestateDeployer.generateReportDirFromEnv(StatePersistenceTest::class.java)
  private var deployer: RestateDeployer? = null

  @BeforeEach
  fun before() {
    this.deployer =
        RestateDeployer.Builder().withServiceEndpoint(Containers.COUNTER_FUNCTION_SPEC).build()
    deployer!!.deployAll(reportDir)
  }

  @AfterEach
  fun after() {
    deployer!!.teardownAll()
  }

  @Test
  fun startAndStopRuntime() {
    val res1 =
        deployer!!.createRuntimeChannel().blockingUseAndTerminate(CounterGrpc::newBlockingStub) {
          it.getAndAdd(CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
        }
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Stop and start the runtime
    deployer!!.teardownRuntime()
    deployer!!.deployRuntime(reportDir)

    val res2 =
        deployer!!
            .createRuntimeChannel() // Port might have been changed when redeploying
            .blockingUseAndTerminate(CounterGrpc::newBlockingStub) {
              it.getAndAdd(
                  CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
            }
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Test
  fun startAndKillRuntime() {
    val res1 =
        deployer!!.createRuntimeChannel().blockingUseAndTerminate(CounterGrpc::newBlockingStub) {
          it.getAndAdd(CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
        }
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Kill and start the runtime
    deployer!!.killRuntime()
    deployer!!.deployRuntime(reportDir)

    val res2 =
        deployer!!
            .createRuntimeChannel() // Port might have been changed when redeploying
            .blockingUseAndTerminate(CounterGrpc::newBlockingStub) {
              it.getAndAdd(
                  CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
            }
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }
}
