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
import dev.restate.client.IngressException
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.future.await
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class KillAndRestartInvocationTest {

  @Service
  @Name("RestartInvocation")
  class RestartInvocation {
    companion object {
      val shouldFail = AtomicBoolean(true)
    }

    @Handler
    suspend fun testHandler(ctx: Context): String {
      ctx.runBlock("my-run") { "something" }

      // Load if we should fail
      val shouldFail = shouldFail.get()
      if (shouldFail) {
        throw IllegalStateException("Sorry, can't make any progress")
      }

      return "Done"
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(RestartInvocation()))
    }
  }

  @Test
  fun killAndRestart(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    // Create clients for the services
    val restartClient =
        KillAndRestartInvocationTestRestartInvocationClient.fromClient(ingressClient)

    // Send request
    val sendResult = restartClient.send().testHandler(init = idempotentCallOptions)
    val blockedFirstRequest = sendResult.attachAsync()
    val invocationId = sendResult.invocationId()

    // Wait for the invocation to reach the error state
    await withAlias
        "invocation is stuck retrying" untilAsserted
        {
          assertThat(getInvocationStatus(adminURI, invocationId).rows)
              .containsOnly(
                  SysInvocationEntry(id = invocationId, status = "backing-off", epoch = 0))
        }

    // Now restart the invocation
    val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
    val invocationApi = InvocationApi(adminClient)
    val result = invocationApi.restartInvocation(invocationId, null, null, null)
    assertThat(result.newInvocationEpoch).isEqualTo(1)

    // At this point, the invocation is still stuck retrying, but the original attach was unblocked
    // with an error
    assertThat(runCatching { blockedFirstRequest.await() }.exceptionOrNull())
        .asInstanceOf(type(IngressException::class.java))
        .returns(471) { it.statusCode }

    // Now let's re-attach, then unblock the service and wait for completion
    val blockedSecondRequest = sendResult.attachAsync()
    RestartInvocation.shouldFail.set(false)

    assertThat(blockedSecondRequest.await().response()).isEqualTo("Done")

    await withAlias
        "got the two invocations in completed state, with same id and different epoch" untilAsserted
        {
          assertThat(getInvocationStatus(adminURI, invocationId).rows)
              .containsExactlyInAnyOrder(
                  SysInvocationEntry(id = invocationId, status = "completed", epoch = 0),
                  SysInvocationEntry(id = invocationId, status = "completed", epoch = 1))
        }
  }
}
