package dev.restate.e2e.runtime

import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.e2e.Containers
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceGrpc
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceGrpc.AwakeableHolderServiceBlockingStub
import dev.restate.e2e.services.awakeableholder.hasAwakeableRequest
import dev.restate.e2e.services.awakeableholder.unlockRequest
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.counterRequest
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.meta.models.CancelInvocationRequest
import dev.restate.generated.IngressGrpc.IngressBlockingStub
import dev.restate.generated.invokeRequest
import java.net.URL
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
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
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(
              Containers.nodeServicesContainer(
                  "services", CounterGrpc.SERVICE_NAME, AwakeableHolderServiceGrpc.SERVICE_NAME))
          .build()
    }
  }

  @Test
  fun kill(
      @InjectBlockingStub ingressClient: IngressBlockingStub,
      @InjectBlockingStub counterClient: CounterBlockingStub,
      @InjectBlockingStub awakeableHolderClient: AwakeableHolderServiceBlockingStub,
      @InjectMetaURL metaURL: URL
  ) {
    val counter = UUID.randomUUID().toString()
    val counterRequest = counterRequest { counterName = counter }

    val sid =
        ingressClient
            .invoke(
                invokeRequest {
                  service = CounterGrpc.SERVICE_NAME
                  method = CounterGrpc.getInfiniteIncrementLoopMethod().bareMethodName!!
                  argument = counterRequest.toByteString()
                })
            .sid

    // Await until AwakeableHolder has an awakeable and then complete it.
    //  With this synchronization point we make sure the invocation has started before killing it.
    await untilCallTo
        {
          awakeableHolderClient.hasAwakeable(hasAwakeableRequest { name = counter })
        } matches
        { result ->
          result!!.hasAwakeable
        }
    awakeableHolderClient.unlock(unlockRequest { name = counter })

    // Kill the invocation
    // We manually do the request with the http client due to
    // https://github.com/cjbooms/fabrikt/issues/225
    val killRequest =
        Request.Builder()
            .url("$metaURL/invocations")
            .delete(
                ObjectMapper()
                    .writeValueAsString(CancelInvocationRequest(sid))
                    .toRequestBody("application/json".toMediaType()))
            .build()
    assertThat(OkHttpClient().newCall(killRequest).execute().code)
        .isGreaterThanOrEqualTo(200)
        .isLessThan(300)

    // Now let's invoke the greeter on the same key.
    // At some point we should get an answer because the service is unlocked by the kill
    // We increment of 1 before entering the awakeable lock
    assertThat(counterClient.get(counterRequest).value).isGreaterThanOrEqualTo(1)
  }
}