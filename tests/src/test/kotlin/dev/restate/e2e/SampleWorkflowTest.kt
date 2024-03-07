// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import java.time.Duration
import kotlin.system.measureNanoTime
import my.restate.e2e.services.CoordinatorClient
import my.restate.e2e.services.CoordinatorComplexRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
class JavaSampleWorkflowTest : BaseSampleWorkflowTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_SERVICE_SPEC)
                .build())
  }
}

abstract class BaseSampleWorkflowTest {
  @Test
  @DisplayName("Sample workflow with sleep, side effect, call and one way call")
  @Execution(ExecutionMode.CONCURRENT)
  fun sampleWorkflow(@InjectIngressClient ingressClient: IngressClient) {
    val sleepDuration = Duration.ofMillis(100L)

    val elapsed = measureNanoTime {
      val value = "foobar"
      val response =
          CoordinatorClient.fromIngress(ingressClient)
              .complex(CoordinatorComplexRequest(sleepDuration.toMillis(), value))

      assertThat(response).isEqualTo(value)
    }

    assertThat(Duration.ofNanos(elapsed)).isGreaterThanOrEqualTo(sleepDuration)
  }
}
