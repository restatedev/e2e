// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import com.google.protobuf.Empty
import dev.restate.admin.api.InvocationApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.TerminationMode
import dev.restate.e2e.Containers
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceGrpc
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceGrpc.AwakeableHolderServiceBlockingStub
import dev.restate.e2e.services.awakeableholder.hasAwakeableRequest
import dev.restate.e2e.services.awakeableholder.unlockRequest
import dev.restate.e2e.services.killtest.KillSingletonServiceGrpc
import dev.restate.e2e.services.killtest.KillSingletonServiceGrpc.KillSingletonServiceBlockingStub
import dev.restate.e2e.services.killtest.KillTestServiceGrpc
import dev.restate.e2e.utils.*
import dev.restate.generated.IngressGrpc
import dev.restate.generated.invokeRequest
import java.net.URL
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KillInvocationTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withServiceEndpoint(
              Containers.nodeServicesContainer(
                  "services",
                  KillTestServiceGrpc.SERVICE_NAME,
                  KillSingletonServiceGrpc.SERVICE_NAME,
                  AwakeableHolderServiceGrpc.SERVICE_NAME))
          .build()
    }
  }

  @Test
  fun kill(
      @InjectBlockingStub ingressClient: IngressGrpc.IngressBlockingStub,
      @InjectBlockingStub singletonService: KillSingletonServiceBlockingStub,
      @InjectBlockingStub awakeableHolderClient: AwakeableHolderServiceBlockingStub,
      @InjectMetaURL metaURL: URL
  ) {
    val id =
        ingressClient
            .invoke(
                invokeRequest {
                  service = KillTestServiceGrpc.SERVICE_NAME
                  method = KillTestServiceGrpc.getStartCallTreeMethod().bareMethodName!!
                  pb = Empty.getDefaultInstance().toByteString()
                })
            .id

    // Await until AwakeableHolder has an awakeable and then complete it.
    // With this synchronization point we make sure the call tree has been built before killing it.
    await untilCallTo
        {
          awakeableHolderClient.hasAwakeable(hasAwakeableRequest { name = "kill" })
        } matches
        { result ->
          result!!.hasAwakeable
        }
    awakeableHolderClient.unlock(unlockRequest { name = "kill" })

    // Kill the invocation
    val client = InvocationApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    client.terminateInvocation(id, TerminationMode.KILL)

    // Check that the singleton service is unlocked after killing the call tree
    singletonService.isUnlocked(Empty.getDefaultInstance())
  }
}
