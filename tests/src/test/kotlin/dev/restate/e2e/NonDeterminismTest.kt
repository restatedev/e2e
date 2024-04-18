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
import dev.restate.sdk.common.CoreSerdes
import dev.restate.sdk.common.Target
import my.restate.e2e.services.CounterClient
import my.restate.e2e.services.NonDeterministicClient
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Tag("only-always-suspending")
class JavaNonDeterminismTest : NonDeterminismTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withInvokerRetryPolicy(RestateDeployer.RetryPolicy.None)
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-non-determinism",
                        NonDeterministicClient.SERVICE_NAME,
                        CounterClient.SERVICE_NAME))
                .build())
  }
}

@Tag("only-always-suspending")
class NodeNonDeterminismTest : NonDeterminismTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                // Disable the retries so we get the error propagated back
                .withInvokerRetryPolicy(RestateDeployer.RetryPolicy.None)
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-non-determinism",
                        NonDeterministicClient.SERVICE_NAME,
                        CounterClient.SERVICE_NAME))
                .build())
  }
}

/** Test non-determinism/journal mismatch checks in the SDKs. */
abstract class NonDeterminismTest {
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings =
          [
              "leftSleepRightCall",
              "callDifferentMethod",
              "backgroundInvokeWithDifferentTargets",
              "setDifferentKey"])
  @Execution(ExecutionMode.CONCURRENT)
  fun method(handlerName: String, @InjectIngressClient ingressClient: IngressClient) {
    Assertions.assertThatThrownBy {
          ingressClient.call(
              Target.virtualObject(NonDeterministicClient.SERVICE_NAME, handlerName, handlerName),
              CoreSerdes.VOID,
              CoreSerdes.VOID,
              null)
        }
        .isNotNull()

    // Assert the counter was not incremented
    assertThat(CounterClient.fromIngress(ingressClient, handlerName).get()).isZero()
  }
}
