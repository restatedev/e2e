package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.multi.BaseCounterTest
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.junit.jupiter.api.extension.RegisterExtension

class CounterTest : BaseCounterTest() {

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
