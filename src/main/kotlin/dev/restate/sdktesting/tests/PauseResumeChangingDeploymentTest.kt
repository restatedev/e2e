// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.InvocationApi
import dev.restate.admin.client.ApiClient
import dev.restate.client.Client
import dev.restate.client.kotlin.attachSuspend
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("only-single-node" /* This test depends on metadata propagation happening immediately */)
class PauseResumeChangingDeploymentTest {

  @Service
  @Name("RetryableService")
  interface RetryableService {
    @Handler suspend fun runRetryableOperation(ctx: Context): String
  }

  class FailingRetryableService : RetryableService {
    override suspend fun runRetryableOperation(ctx: Context): String {
      return ctx.runBlock { throw RuntimeException("This should fail in old version") }
    }
  }

  class FixedRetryableService : RetryableService {
    override suspend fun runRetryableOperation(ctx: Context): String {
      return ctx.runBlock { "Success in new version!" }
    }
  }

  companion object {
    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      // Enable pause on max attempts with fast retries
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__INITIAL_INTERVAL", "10ms")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__MAX_INTERVAL", "10ms")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__MAX_ATTEMPTS", "10")
      withEnv("RESTATE_DEFAULT_RETRY_POLICY__ON_MAX_ATTEMPTS", "pause")

      // Deploy only the failing implementation initially
      withEndpoint(Endpoint.bind(FailingRetryableService()))
    }
  }

  @Test
  fun pauseAndResumeInvocation(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    // Create client for RetryableService
    val retryClient =
        PauseResumeChangingDeploymentTestRetryableServiceClient.fromClient(ingressClient)

    // Send idempotent request to trigger retries and pause
    val sendResult = retryClient.send().runRetryableOperation(init = idempotentCallOptions)
    val invocationId = sendResult.invocationId()

    // Wait until the invocation is paused (or suspended) by the runtime
    await withAlias
        "invocation is paused or suspended" untilAsserted
        {
          val status = getInvocationStatus(adminURI, invocationId)
          assertThat(status.status).isIn("paused", "suspended")
        }

    // Start a new local endpoint exposing the fixed implementation and keep it alive
    startAndRegisterLocalEndpoint(Endpoint.bind(FixedRetryableService()).build(), adminURI).use {
        local ->
      // Resume the paused invocation on the specific endpoint
      val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
      val invocationApi = InvocationApi(adminClient)
      retryOnServiceUnavailable { invocationApi.resumeInvocation(invocationId, local.deploymentId) }

      assertThat(sendResult.attachSuspend().response()).isEqualTo("Success in new version!")

      val status = getInvocationStatus(adminURI, invocationId)
      assertThat(status.status).isEqualTo("completed")
    }
  }
}
