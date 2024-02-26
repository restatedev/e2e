// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.admin.api.InvocationApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.TerminationMode
import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.InjectMetaURL
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import java.net.URL
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import my.restate.e2e.services.AwakeableHolderClient
import my.restate.e2e.services.CancelTest
import my.restate.e2e.services.CancelTestBlockingServiceClient
import my.restate.e2e.services.CancelTestRunnerClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.junit.jupiter.api.Disabled
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
                        CancelTestRunnerClient.COMPONENT_NAME,
                        CancelTestBlockingServiceClient.COMPONENT_NAME,
                        AwakeableHolderClient.COMPONENT_NAME))
                .build())
  }
}

@Disabled("node-services is not ready with the new interfaces")
class NodeCancelInvocationTest : BaseCancelInvocationTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                // TODO update once we convert the node tests
                //                .withServiceEndpoint(
                //                    Containers.nodeServicesContainer(
                //                        "node-cancel-invocation",
                //
                //
                // CancelTestServiceGrpc.SERVICE_NAME,
                //                                                BlockingServiceGrpc.SERVICE_NAME,
                //
                // AwakeableHolderServiceGrpc.SERVICE_NAME
                //                    ))
                .build())
  }
}

abstract class BaseCancelInvocationTest {
  @ParameterizedTest(name = "cancel blocked invocation on {0}")
  @EnumSource(value = CancelTest.BlockingOperation::class)
  fun cancelInvocation(
      blockingOperation: CancelTest.BlockingOperation,
      @InjectIngressClient ingressClient: IngressClient,
      @InjectMetaURL metaURL: URL,
  ) {
    val key = UUID.randomUUID().toString()
    val cancelTestClient = CancelTestRunnerClient.fromIngress(ingressClient, key)
    val blockingServiceClient = CancelTestBlockingServiceClient.fromIngress(ingressClient, key)

    val id = cancelTestClient.send().startTest(blockingOperation)

    val awakeableHolderClient = AwakeableHolderClient.fromIngress(ingressClient, "cancel")

    await until { awakeableHolderClient.hasAwakeable() }

    awakeableHolderClient.unlock("cancel")

    val client = InvocationApi(ApiClient().setHost(metaURL.host).setPort(metaURL.port))

    // The termination signal might arrive before the blocking call to the cancel singleton was
    // made, so we need to retry.
    await.ignoreException(TimeoutCancellationException::class.java).until {
      client.terminateInvocation(id, TerminationMode.CANCEL)
      runBlocking { withTimeout(1.seconds) { cancelTestClient.verifyTest() } }
    }

    // Check that the singleton service is unlocked
    blockingServiceClient.isUnlocked()
  }
}
