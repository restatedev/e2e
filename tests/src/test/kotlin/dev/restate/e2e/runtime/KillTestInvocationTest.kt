// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.admin.api.InvocationApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.TerminationMode
import dev.restate.e2e.Containers
import dev.restate.e2e.utils.*
import dev.restate.sdk.client.IngressClient
import java.net.URL
import my.restate.e2e.services.AwakeableHolderClient
import my.restate.e2e.services.KillTestRunnerClient
import my.restate.e2e.services.KillTestSingletonClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KillTestInvocationTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withServiceEndpoint(
              Containers.javaServicesContainer(
                  "services",
                  KillTestRunnerClient.COMPONENT_NAME,
                  KillTestSingletonClient.COMPONENT_NAME,
                  AwakeableHolderClient.COMPONENT_NAME))
          .build()
    }
  }

  @Test
  fun kill(@InjectIngressClient ingressClient: IngressClient, @InjectMetaURL metaURL: URL) {
    val id = KillTestRunnerClient.fromIngress(ingressClient).send().startCallTree()
    val awakeableHolderClient = AwakeableHolderClient.fromIngress(ingressClient, "kill")

    // Await until AwakeableHolder has an awakeable and then complete it.
    // With this synchronization point we make sure the call tree has been built before killing it.
    await until { awakeableHolderClient.hasAwakeable() }
    awakeableHolderClient.unlock("")

    // Kill the invocation
    val client = InvocationApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    client.terminateInvocation(id, TerminationMode.KILL)

    // Check that the singleton service is unlocked after killing the call tree
    KillTestSingletonClient.fromIngress(ingressClient, "").isUnlocked()
  }
}
