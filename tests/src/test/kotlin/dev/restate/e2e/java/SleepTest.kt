package dev.restate.e2e.java

import dev.restate.e2e.Containers
import dev.restate.e2e.multi.BaseSleepTest
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class SleepTest : BaseSleepTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_FUNCTION_SPEC)
                .build())
  }
}
