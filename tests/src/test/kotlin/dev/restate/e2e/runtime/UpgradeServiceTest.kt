package dev.restate.e2e.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Empty
import dev.restate.e2e.Containers
import dev.restate.e2e.services.collections.list.ListProto
import dev.restate.e2e.services.collections.list.ListServiceGrpc
import dev.restate.e2e.services.upgradetest.AwakeableHolderServiceGrpc
import dev.restate.e2e.services.upgradetest.UpgradeTestServiceGrpc
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.meta.client.EndpointsClient
import dev.restate.e2e.utils.meta.models.RegisterServiceEndpointRequest
import dev.restate.generated.IngressGrpc
import dev.restate.generated.invokeRequest
import java.net.URL
import java.util.*
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
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
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(
              Containers.javaServicesContainer(
                      "version1",
                      UpgradeTestServiceGrpc.SERVICE_NAME,
                      AwakeableHolderServiceGrpc.SERVICE_NAME,
                      ListServiceGrpc.SERVICE_NAME)
                  .withEnv("E2E_UPGRADETEST_VERSION", "v1"))
          .withServiceEndpoint(
              Containers.javaServicesContainer("version2", UpgradeTestServiceGrpc.SERVICE_NAME)
                  .withEnv("E2E_UPGRADETEST_VERSION", "v2")
                  .skipRegistration())
          .build()
    }

    fun registerService2(metaURL: URL) {
      val client = EndpointsClient(ObjectMapper(), metaURL.toString(), OkHttpClient())
      val registrationResult =
          client.createServiceEndpoint(
              RegisterServiceEndpointRequest(
                  uri = "http://version2:8080/",
                  additionalHeaders = null,
                  force = false))
      assertThat(registrationResult.statusCode).isGreaterThanOrEqualTo(200).isLessThan(300)
    }
  }

  @Test
  fun executesNewInvocationWithLatestServiceRevisions(
      @InjectBlockingStub upgradeTestClient: UpgradeTestServiceGrpc.UpgradeTestServiceBlockingStub,
      @InjectMetaURL metaURL: URL
  ) {
    // Execute the first request
    val firstResult = upgradeTestClient.executeSimple(Empty.getDefaultInstance())
    assertThat(firstResult.message).isEqualTo("v1")

    // Now register the update
    registerService2(metaURL)

    // After the update, the runtime might not immediately propagate the usage of the new version
    // (this effectively depends on implementation details).
    // For this reason, we try to invoke the upgrade test method several times until we see the new
    // version running
    await untilCallTo
        {
          upgradeTestClient.executeSimple(Empty.getDefaultInstance())
        } matches
        { result ->
          result!!.message == "v2"
        }
  }

  @Test
  fun inFlightInvocation(
      @InjectBlockingStub ingressClient: IngressGrpc.IngressBlockingStub,
      @InjectBlockingStub upgradeTestClient: UpgradeTestServiceGrpc.UpgradeTestServiceBlockingStub,
      @InjectBlockingStub
      awakeableHolderClient: AwakeableHolderServiceGrpc.AwakeableHolderServiceBlockingStub,
      @InjectBlockingStub listClient: ListServiceGrpc.ListServiceBlockingStub,
      @InjectMetaURL metaURL: URL
  ) {
    inFlightInvocationtest(
        ingressClient, upgradeTestClient, awakeableHolderClient, listClient, metaURL)
  }

  @Test
  fun inFlightInvocationStoppingTheRuntime(
      @InjectBlockingStub ingressClient: IngressGrpc.IngressBlockingStub,
      @InjectBlockingStub upgradeTestClient: UpgradeTestServiceGrpc.UpgradeTestServiceBlockingStub,
      @InjectBlockingStub
      awakeableHolderClient: AwakeableHolderServiceGrpc.AwakeableHolderServiceBlockingStub,
      @InjectBlockingStub listClient: ListServiceGrpc.ListServiceBlockingStub,
      @InjectMetaURL metaURL: URL,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeContainer: ContainerHandle
  ) {
    inFlightInvocationtest(
        ingressClient, upgradeTestClient, awakeableHolderClient, listClient, metaURL) {
      runtimeContainer.terminateAndRestart()
    }
  }

  fun inFlightInvocationtest(
      ingressClient: IngressGrpc.IngressBlockingStub,
      upgradeTestClient: UpgradeTestServiceGrpc.UpgradeTestServiceBlockingStub,
      awakeableHolderClient: AwakeableHolderServiceGrpc.AwakeableHolderServiceBlockingStub,
      listClient: ListServiceGrpc.ListServiceBlockingStub,
      metaURL: URL,
      restartRuntimeFn: () -> Unit = {},
  ) {
    // Invoke the upgrade test complex method
    ingressClient.invoke(
        invokeRequest {
          service = UpgradeTestServiceGrpc.SERVICE_NAME
          method = UpgradeTestServiceGrpc.getExecuteComplexMethod().bareMethodName.toString()
        })

    // Await until AwakeableHolder has an awakeable
    await untilCallTo
        {
          awakeableHolderClient.hasAwakeable(Empty.getDefaultInstance())
        } matches
        { result ->
          result!!.hasAwakeable
        }

    // Now register the update
    registerService2(metaURL)

    // Let's wait for at least once returning v2
    await untilCallTo
        {
          upgradeTestClient.executeSimple(Empty.getDefaultInstance())
        } matches
        { result ->
          result!!.message == "v2"
        }

    restartRuntimeFn()

    // Now let's resume the awakeable
    awakeableHolderClient.unlock(Empty.getDefaultInstance())

    // Let's wait for the list service to contain "v1" once
    await untilAsserted
        {
          assertThat(
                  listClient
                      .get(ListProto.Request.newBuilder().setListName("upgrade-test").build())
                      .valuesList)
              .containsOnlyOnce("v1")
        }
  }
}
