package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.GrpcUtils.blockingUseAndTerminate
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.CounterRequest
import dev.restate.e2e.functions.counter.NoopGrpc
import dev.restate.e2e.utils.RestateDeployer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.until
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Tag("requires-persistence-layer")
class FailpointsTest {

  private val reportDir: String =
      RestateDeployer.generateReportDirFromEnv(FailpointsTest::class.java)
  private var deployer: RestateDeployer? = null

  @BeforeEach
  fun before() {
    this.deployer =
        RestateDeployer.Builder()
            .withFunction(Containers.COUNTER_FUNCTION_SPEC)
            .withConfigEntries("num_partitions", 10)
            .build()
    deployer!!.deployFunctions(reportDir)
    deployer!!.deployAdditionalContainers(reportDir)
  }

  @AfterEach
  fun after() {
    deployer!!.teardownAll()
  }

  @ValueSource(
      strings =
          [
              // The first time is off because the first message is the doAndReportInvocationCount
              "send-to-partition-after-ack=1*print(disabled)->panic",
              "leader-before-apply-side-effects=panic",
              "leader-after-apply-side-effects=panic"])
  @ParameterizedTest(name = "failpoint {0}")
  fun deduplication(failpoint: String) {
    deployer!!.deployRuntime(reportDir, mapOf("FAILPOINTS" to failpoint))

    deployer!!.createRuntimeChannel().blockingUseAndTerminate(NoopGrpc::newBlockingStub) {
      try {
        it.doAndReportInvocationCount(Empty.getDefaultInstance())
      } catch (e: Throwable) {
        // Ignore failure
      }
    }

    // Wait for runtime to die
    await until { !deployer!!.isRuntimeRunning() }

    // Cleanup container and deploy it again
    deployer!!.killRuntime()
    deployer!!.deployRuntime(reportDir)

    // Check the value inside counter
    // Note that this should be executed in any case after the add done by the noop method,
    // because of the ordering guarantees
    await untilCallTo
        {
          deployer!!.createRuntimeChannel().blockingUseAndTerminate(CounterGrpc::newBlockingStub) {
            it.get(CounterRequest.newBuilder().setCounterName("doAndReportInvocationCount").build())
          }
        } matches
        { res ->
          res!!.value == 1L
        }

    // Query again because in case of missing deduplication this becomes 2
    val res =
        deployer!!.createRuntimeChannel().blockingUseAndTerminate(CounterGrpc::newBlockingStub) {
          it.get(CounterRequest.newBuilder().setCounterName("doAndReportInvocationCount").build())
        }
    assertThat(res.value).isEqualTo(1)
  }
}
