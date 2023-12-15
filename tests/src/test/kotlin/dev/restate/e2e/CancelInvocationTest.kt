// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.admin.api.InvocationApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.TerminationMode
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceGrpc
import dev.restate.e2e.services.awakeableholder.hasAwakeableRequest
import dev.restate.e2e.services.awakeableholder.unlockRequest
import dev.restate.e2e.services.canceltest.*
import dev.restate.e2e.services.canceltest.CancelTestProto.BlockingOperation
import dev.restate.e2e.utils.*
import dev.restate.generated.IngressGrpc.IngressBlockingStub
import dev.restate.generated.invokeRequest
import io.grpc.Channel
import java.net.URL
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class JavaCancelInvocationTest : BaseCancelInvocationTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.javaServicesContainer(
                        "java-cancel-invocation",
                        CancelTestServiceGrpc.SERVICE_NAME,
                        BlockingServiceGrpc.SERVICE_NAME))
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "awakeable-holder", AwakeableHolderServiceGrpc.SERVICE_NAME))
                .build())
  }
}

class NodeCancelInvocationTest : BaseCancelInvocationTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-cancel-invocation",
                        CancelTestServiceGrpc.SERVICE_NAME,
                        BlockingServiceGrpc.SERVICE_NAME,
                        AwakeableHolderServiceGrpc.SERVICE_NAME))
                .build())
  }
}

abstract class BaseCancelInvocationTest {
  @ParameterizedTest(name = "cancel blocked invocation on {0}")
  @EnumSource(
      value = BlockingOperation::class, names = ["UNRECOGNIZED"], mode = EnumSource.Mode.EXCLUDE)
  fun cancelInvocation(
      blockingOperation: BlockingOperation,
      @InjectBlockingStub ingressClient: IngressBlockingStub,
      @InjectChannel channel: Channel,
      @InjectBlockingStub blockingServiceClient: BlockingServiceGrpc.BlockingServiceBlockingStub,
      @InjectBlockingStub
      awakeableHolderClient: AwakeableHolderServiceGrpc.AwakeableHolderServiceBlockingStub,
      @InjectMetaURL metaURL: URL,
  ) {
    val request = blockingRequest { operation = blockingOperation }

    val id =
        ingressClient
            .invoke(
                invokeRequest {
                  service = CancelTestServiceGrpc.SERVICE_NAME
                  method = CancelTestServiceGrpc.getStartTestMethod().bareMethodName!!
                  pb = request.toByteString()
                })
            .id

    await untilCallTo
        {
          awakeableHolderClient.hasAwakeable(hasAwakeableRequest { name = "cancel" })
        } matches
        { result ->
          result!!.hasAwakeable
        }

    awakeableHolderClient.unlock(unlockRequest { name = "cancel" })

    val client = InvocationApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))
    val cancelTestClient = CancelTestServiceGrpcKt.CancelTestServiceCoroutineStub(channel)

    // The termination signal might arrive before the blocking call to the cancel singleton was
    // made, so we need to retry.
    await.ignoreException(TimeoutCancellationException::class.java).until {
      client.terminateInvocation(id, TerminationMode.CANCEL)
      runBlocking {
        withTimeout(1.seconds) {
          cancelTestClient.verifyTest(Empty.getDefaultInstance()).isCanceled
        }
      }
    }

    // Check that the singleton service is unlocked
    blockingServiceClient.isUnlocked(Empty.getDefaultInstance())
  }
}
