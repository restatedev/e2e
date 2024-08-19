// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.admin.api.DeploymentApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.RegisterDeploymentRequest
import dev.restate.admin.model.RegisterDeploymentRequestAnyOf
import dev.restate.e2e.utils.InjectClient
import dev.restate.e2e.utils.InjectMetaURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerForEachExtension
import dev.restate.sdk.JsonSerdes
import dev.restate.sdk.client.Client
import java.net.URL
import java.util.*
import my.restate.e2e.services.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class RetryOnUnknownServiceTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withServiceEndpoint(
              Containers.javaServicesContainer(
                      "java-proxy", VirtualObjectProxyDefinitions.SERVICE_NAME)
                  .build())
          .withServiceEndpoint(
              Containers.JAVA_COLLECTIONS_SERVICE_SPEC.copy(skipRegistration = true))
          .build()
    }

    fun registerListService(metaURL: URL) {
      val client = DeploymentApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
      client.createDeployment(
          RegisterDeploymentRequest(
              RegisterDeploymentRequestAnyOf()
                  .uri("http://${Containers.JAVA_COLLECTIONS_SERVICE_SPEC.hostName}:8080/")
                  .force(false)))
    }
  }

  @Test
  fun retryOnUnknownServiceUsingCall(
      @InjectClient ingressClient: Client,
      @InjectMetaURL metaURL: URL
  ) {
    retryOnUnknownTest(ingressClient, metaURL) {
      VirtualObjectProxyClient.fromClient(ingressClient).send().call(it)
    }
  }

  @Test
  fun retryOnUnknownServiceUsingOneWayCall(
      @InjectClient ingressClient: Client,
      @InjectMetaURL metaURL: URL
  ) {
    retryOnUnknownTest(ingressClient, metaURL) {
      VirtualObjectProxyClient.fromClient(ingressClient).send().oneWayCall(it)
    }
  }

  private fun retryOnUnknownTest(
      @InjectClient ingressClient: Client,
      metaURL: URL,
      action: (VirtualObjectProxy.Request) -> Unit
  ) {
    val list = UUID.randomUUID().toString()
    val valueToAppend = "a"
    val request =
        VirtualObjectProxy.Request(
            ListObjectDefinitions.SERVICE_NAME,
            list,
            "append",
            JsonSerdes.STRING.serialize(valueToAppend))

    // We invoke the AwakeableGuardedProxyService through the ingress service
    action(request)

    // Await until we got a try count of 2
    await untilCallTo
        {
          VirtualObjectProxyClient.fromClient(ingressClient).getRetryCount(request)
        } matches
        { result ->
          result!! >= 2
        }

    // Register list service
    registerListService(metaURL)

    // Let's wait for the list service to contain "a" once
    await untilAsserted
        {
          assertThat(ListObjectClient.fromClient(ingressClient, list).get())
              .containsOnlyOnce(valueToAppend)
        }
  }
}
