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
import dev.restate.sdk.client.Client
import java.net.URL
import my.restate.e2e.services.*
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
                  KillTestRunnerDefinitions.SERVICE_NAME,
                  KillTestSingletonDefinitions.SERVICE_NAME,
                  AwakeableHolderDefinitions.SERVICE_NAME))
          .build()
    }
  }

  @Test
  fun kill(@InjectClient ingressClient: Client, @InjectMetaURL metaURL: URL) {
    val id = KillTestRunnerClient.fromClient(ingressClient).send().startCallTree().invocationId
    val awakeableHolderClient = AwakeableHolderClient.fromClient(ingressClient, "kill")

    // Await until AwakeableHolder has an awakeable and then complete it.
    // With this synchronization point we make sure the call tree has been built before killing it.
    await until { awakeableHolderClient.hasAwakeable() }
    awakeableHolderClient.unlock("")

    // Kill the invocation
    val client = InvocationApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    client.terminateInvocation(id, TerminationMode.KILL)

    // Check that the singleton service is unlocked after killing the call tree
    KillTestSingletonClient.fromClient(ingressClient, "").isUnlocked()
  }
}
