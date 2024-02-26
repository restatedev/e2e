// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.e2e.Containers
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import java.util.UUID
import java.util.stream.Stream
import my.restate.e2e.services.CoordinatorClient
import my.restate.e2e.services.CoordinatorInvokeSequentiallyRequest
import my.restate.e2e.services.ListObjectClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** Test the ordering is respected between invoke and background invoke */
class InvokeOrderingTest {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.JAVA_COORDINATOR_SERVICE_SPEC)
                .withServiceEndpoint(Containers.JAVA_COLLECTIONS_SERVICE_SPEC)
                .build())

    @JvmStatic
    fun ordering(): Stream<Arguments> {
      // To enforce ordering wrt listClient.clear(...) executed in the test code,
      // the last call must be sync!
      return Stream.of(
          Arguments.of(booleanArrayOf(true, false, false)),
          Arguments.of(booleanArrayOf(false, true, false)),
          Arguments.of(
              booleanArrayOf(true, true, false),
          ))
    }
  }

  @ParameterizedTest
  @MethodSource
  @Execution(ExecutionMode.CONCURRENT)
  fun ordering(
      ordering: BooleanArray,
      @InjectIngressClient ingressClient: IngressClient,
  ) {
    val listName = UUID.randomUUID().toString()

    CoordinatorClient.fromIngress(ingressClient)
        .invokeSequentially(CoordinatorInvokeSequentiallyRequest(ordering.asList(), listName))

    assertThat(ListObjectClient.fromIngress(ingressClient, listName).clear())
        .containsExactly("0", "1", "2")
  }
}
