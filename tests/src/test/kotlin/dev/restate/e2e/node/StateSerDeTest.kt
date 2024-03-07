// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import my.restate.e2e.services.ListObjectClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/** Test that we can ser/de proto generated objects (check the source of ListService.append) */
@Tag("always-suspending")
@Disabled("node-services is not ready with the new interfaces")
class StateSerDeTest {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(Containers.NODE_COLLECTIONS_SERVICE_SPEC)
                .build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun addAndClear(@InjectIngressClient ingressClient: IngressClient) {
    val listA = ListObjectClient.fromIngress(ingressClient, "list-a")
    val listB = ListObjectClient.fromIngress(ingressClient, "list-b")

    listA.append("1")
    listB.append("2")
    listA.append("3")
    listB.append("4")
    listA.append("5")
    listB.append("6")

    val listAContent = listA.clear()
    val listBContent = listB.clear()

    assertThat(listAContent).containsExactly("1", "3", "5")
    assertThat(listBContent).containsExactly("2", "4", "6")
  }
}
