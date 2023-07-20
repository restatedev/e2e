package dev.restate.e2e.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.e2e.Containers
import dev.restate.e2e.services.collections.list.ListProto
import dev.restate.e2e.services.collections.list.ListServiceGrpc
import dev.restate.e2e.services.collections.list.ListServiceGrpc.ListServiceBlockingStub
import dev.restate.e2e.services.collections.list.appendRequest
import dev.restate.e2e.services.proxy.ProxyServiceGrpc
import dev.restate.e2e.services.proxy.ProxyServiceGrpc.ProxyServiceBlockingStub
import dev.restate.e2e.services.proxy.request
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.meta.client.EndpointsClient
import dev.restate.e2e.utils.meta.models.RegisterServiceEndpointRequest
import dev.restate.generated.IngressGrpc.IngressBlockingStub
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
class RetryOnUnknownServiceTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withInvokerRetryPolicy(RestateDeployer.RetryPolicy.FixedDelay("100ms", 100))
          .withServiceEndpoint(Containers.NODE_PROXY_SERVICE_SPEC)
          .withServiceEndpoint(
              Containers.NODE_COLLECTIONS_SERVICE_SPEC.copy(skipRegistration = true))
          .build()
    }

    fun registerListService(metaURL: URL) {
      val client = EndpointsClient(ObjectMapper(), metaURL.toString(), OkHttpClient())
      val registrationResult =
          client.createServiceEndpoint(
              RegisterServiceEndpointRequest(
                  uri = "http://${Containers.NODE_COLLECTIONS_SERVICE_SPEC.hostName}:8080/",
                  additionalHeaders = null,
                  force = false))
      assertThat(registrationResult.statusCode).isGreaterThanOrEqualTo(200).isLessThan(300)
    }
  }

  @Test
  fun retryOnUnknownServiceUsingCall(
      @InjectBlockingStub ingressClient: IngressBlockingStub,
      @InjectBlockingStub proxyServiceGrpc: ProxyServiceBlockingStub,
      @InjectBlockingStub listClient: ListServiceBlockingStub,
      @InjectMetaURL metaURL: URL
  ) {
    retryOnUnknownTest(
        ingressClient,
        proxyServiceGrpc,
        listClient,
        metaURL,
        ProxyServiceGrpc.getCallMethod().bareMethodName!!)
  }

  @Test
  fun retryOnUnknownServiceUsingOneWayCall(
      @InjectBlockingStub ingressClient: IngressBlockingStub,
      @InjectBlockingStub proxyServiceGrpc: ProxyServiceBlockingStub,
      @InjectBlockingStub listClient: ListServiceBlockingStub,
      @InjectMetaURL metaURL: URL
  ) {
    retryOnUnknownTest(
        ingressClient,
        proxyServiceGrpc,
        listClient,
        metaURL,
        ProxyServiceGrpc.getOneWayCallMethod().bareMethodName!!)
  }

  private fun retryOnUnknownTest(
      ingressClient: IngressBlockingStub,
      proxyServiceGrpc: ProxyServiceBlockingStub,
      listClient: ListServiceBlockingStub,
      metaURL: URL,
      methodName: String
  ) {
    val list = UUID.randomUUID().toString()
    val valueToAppend = "a"
    val request = request {
      serviceName = ListServiceGrpc.SERVICE_NAME
      serviceMethod = ListServiceGrpc.getAppendMethod().bareMethodName!!
      message =
          appendRequest {
                listName = list
                value = valueToAppend
              }
              .toByteString()
    }

    // We invoke the AwakeableGuardedProxyService through the ingress service
    ingressClient.invoke(
        invokeRequest {
          service = ProxyServiceGrpc.SERVICE_NAME
          method = methodName
          argument = request.toByteString()
        })

    // Await until we got a try count of 2
    await untilCallTo
        {
          proxyServiceGrpc.getRetryCount(request)
        } matches
        { result ->
          result!!.count >= 2
        }

    // Register list service
    registerListService(metaURL)

    // Let's wait for the list service to contain "a" once
    await untilAsserted
        {
          assertThat(
                  listClient
                      .get(ListProto.Request.newBuilder().setListName(list).build())
                      .valuesList)
              .containsOnlyOnce(valueToAppend)
        }
  }
}
