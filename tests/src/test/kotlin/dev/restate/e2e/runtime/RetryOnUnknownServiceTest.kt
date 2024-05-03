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
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.InjectMetaURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerForEachExtension
import dev.restate.sdk.client.IngressClient
import dev.restate.sdk.common.CoreSerdes
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
      @InjectIngressClient ingressClient: IngressClient,
      @InjectMetaURL metaURL: URL
  ) {
    retryOnUnknownTest(ingressClient, metaURL) {
      VirtualObjectProxyClient.fromIngress(ingressClient).send().call(it)
    }
  }

  @Test
  fun retryOnUnknownServiceUsingOneWayCall(
      @InjectIngressClient ingressClient: IngressClient,
      @InjectMetaURL metaURL: URL
  ) {
    retryOnUnknownTest(ingressClient, metaURL) {
      VirtualObjectProxyClient.fromIngress(ingressClient).send().oneWayCall(it)
    }
  }

  private fun retryOnUnknownTest(
      @InjectIngressClient ingressClient: IngressClient,
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
            CoreSerdes.JSON_STRING.serialize(valueToAppend))

    // We invoke the AwakeableGuardedProxyService through the ingress service
    action(request)

    // Await until we got a try count of 2
    await untilCallTo
        {
          VirtualObjectProxyClient.fromIngress(ingressClient).getRetryCount(request)
        } matches
        { result ->
          result!! >= 2
        }

    // Register list service
    registerListService(metaURL)

    // Let's wait for the list service to contain "a" once
    await untilAsserted
        {
          assertThat(ListObjectClient.fromIngress(ingressClient, list).get())
              .containsOnlyOnce(valueToAppend)
        }
  }
}
