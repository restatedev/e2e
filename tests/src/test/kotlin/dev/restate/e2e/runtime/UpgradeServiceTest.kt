// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.admin.api.DeploymentApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.RegisterDeploymentRequest
import dev.restate.admin.model.RegisterDeploymentRequestAnyOf
import dev.restate.e2e.Containers
import dev.restate.e2e.utils.*
import dev.restate.sdk.client.IngressClient
import java.net.URL
import my.restate.e2e.services.AwakeableHolderClient
import my.restate.e2e.services.ListObjectClient
import my.restate.e2e.services.UpgradeTestClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.until
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class UpgradeServiceTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withServiceEndpoint(
              Containers.javaServicesContainer(
                      "version1", UpgradeTestClient.SERVICE_NAME, ListObjectClient.SERVICE_NAME)
                  .withEnv("E2E_UPGRADETEST_VERSION", "v1"))
          .withServiceEndpoint(
              Containers.javaServicesContainer(
                  "awakeable-holder", AwakeableHolderClient.SERVICE_NAME))
          .withServiceEndpoint(
              Containers.javaServicesContainer("version2", UpgradeTestClient.SERVICE_NAME)
                  .withEnv("E2E_UPGRADETEST_VERSION", "v2")
                  .skipRegistration())
          .build()
    }

    fun registerService2(metaURL: URL) {
      val client = DeploymentApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
      client.createDeployment(
          RegisterDeploymentRequest(
              RegisterDeploymentRequestAnyOf().uri("http://version2:8080/").force(false)))
    }
  }

  @Test
  fun executesNewInvocationWithLatestServiceRevisions(
      @InjectIngressClient ingressClient: IngressClient,
      @InjectMetaURL metaURL: URL
  ) {
    val upgradeTestClient = UpgradeTestClient.fromIngress(ingressClient)

    // Execute the first request
    val firstResult = upgradeTestClient.executeSimple()
    assertThat(firstResult).isEqualTo("v1")

    // Now register the update
    registerService2(metaURL)

    // After the update, the runtime might not immediately propagate the usage of the new version
    // (this effectively depends on implementation details).
    // For this reason, we try to invoke the upgrade test method several times until we see the new
    // version running
    await untilCallTo { upgradeTestClient.executeSimple() } matches { result -> result!! == "v2" }
  }

  @Test
  fun inFlightInvocation(
      @InjectIngressClient ingressClient: IngressClient,
      @InjectMetaURL metaURL: URL
  ) {
    inFlightInvocationtest(ingressClient, metaURL)
  }

  @Test
  fun inFlightInvocationStoppingTheRuntime(
      @InjectIngressClient ingressClient: IngressClient,
      @InjectMetaURL metaURL: URL,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
  ) {
    inFlightInvocationtest(ingressClient, metaURL) { runtimeContainer.terminateAndRestart() }
  }

  fun inFlightInvocationtest(
      ingressClient: IngressClient,
      metaURL: URL,
      restartRuntimeFn: () -> Unit = {},
  ) {
    val upgradeTestClient = UpgradeTestClient.fromIngress(ingressClient)

    // Invoke the upgrade test complex method
    upgradeTestClient.send().executeComplex()

    // Await until AwakeableHolder has an awakeable
    val awakeableHolderClient = AwakeableHolderClient.fromIngress(ingressClient, "upgrade")
    await until { awakeableHolderClient.hasAwakeable() }

    // Now register the update
    registerService2(metaURL)

    // Let's wait for at least once returning v2
    await untilCallTo { upgradeTestClient.executeSimple() } matches { result -> result!! == "v2" }

    restartRuntimeFn()

    // Now let's resume the awakeable
    awakeableHolderClient.unlock("")

    // Let's wait for the list service to contain "v1" once
    await untilAsserted
        {
          assertThat(ListObjectClient.fromIngress(ingressClient, "upgrade-test").get())
              .containsOnlyOnce("v1")
        }
  }
}
