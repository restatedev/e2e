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
import dev.restate.client.kotlin.*
import dev.restate.client.kotlin.attachSuspend
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.days
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories.STRING
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicReference

class RestartAsNewInvocationTest {

  @Service
  class RestartInvocation {
    companion object {
      val shouldFail = AtomicBoolean(true)
      val ctxRunResult = AtomicReference("old")
    }

    @Handler
    suspend fun echo(ctx: Context, input: String): String {
      val from = ctx.runBlock { ctxRunResult.get() }

      // Load if we should fail
      val shouldFail = shouldFail.get()
      if (shouldFail) {
        throw TerminalException("Sorry, can't make any progress")
      }

      return "$input from $from"
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(RestartInvocation()) { it.journalRetention = 1.days })
    }
  }

  @Test
  fun restartCompletedWithTerminalException(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    // First attempt should fail
    RestartInvocation.shouldFail.set(true)
    RestartInvocation.ctxRunResult.set("old")

    val input = "my-input"

    // Create clients for the services
    val restartClient = RestartAsNewInvocationTestRestartInvocationClient.fromClient(ingressClient)

    // Send request first time
    val sendResult = restartClient.send().echo(input, init = idempotentCallOptions)
    val initialInvocationId = sendResult.invocationId()

    // Should fail with terminal exception
    assertThat(runCatching { sendResult.attach() })
        .extracting { it.exceptionOrNull() }
        .asInstanceOf(type(IngressException::class.java))
        .extracting({ it.message }, STRING)
        .contains("Sorry, can't make any progress")

    // Next attempt must not fail
    RestartInvocation.shouldFail.set(false)
    RestartInvocation.ctxRunResult.set("new")

    // Now restart invocation
    val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
    val invocationApi = InvocationApi(adminClient)
    val newInvocationId =
        invocationApi.restartAsNewInvocation(sendResult.invocationId(), null, null).newInvocationId

    // Assert this returns the input
    val newInvocationResult =
        ingressClient.invocationHandle(newInvocationId, String::class.java).attachSuspend()
    assertThat(newInvocationResult.response).isEqualTo("$input from new")

    // Assert we both got invocation status completed
    await withAlias
        "got the two invocation status completed" untilAsserted
        {
          assertThat(getInvocationStatus(adminURI, initialInvocationId))
              .isEqualTo(
                  SysInvocationEntry(id = initialInvocationId, status = "completed"),
              )
          assertThat(getInvocationStatus(adminURI, newInvocationId))
              .isEqualTo(
                  SysInvocationEntry(id = newInvocationId, status = "completed"),
              )
        }
  }

  @Test
  fun restartFromPrefix(
    @InjectClient ingressClient: Client,
    @InjectAdminURI adminURI: URI,
  ) = runTest {
    // First attempt should fail
    RestartInvocation.shouldFail.set(true)
    RestartInvocation.ctxRunResult.set("old")

    val input = "my-input"

    // Create clients for the services
    val restartClient = RestartAsNewInvocationTestRestartInvocationClient.fromClient(ingressClient)

    // Send request first time
    val sendResult = restartClient.send().echo(input, init = idempotentCallOptions)
    val initialInvocationId = sendResult.invocationId()

    // Should fail with terminal exception
    assertThat(runCatching { sendResult.attach() })
      .extracting { it.exceptionOrNull() }
      .asInstanceOf(type(IngressException::class.java))
      .extracting({ it.message }, STRING)
      .contains("Sorry, can't make any progress")

    // Next attempt must not fail
    RestartInvocation.shouldFail.set(false)
    RestartInvocation.ctxRunResult.set("new")

    // Now restart invocation
    val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
    val invocationApi = InvocationApi(adminClient)
    val newInvocationId =
      invocationApi.restartAsNewInvocation(sendResult.invocationId(), 1, null).newInvocationId

    // Assert this returns the input
    val newInvocationResult =
      ingressClient.invocationHandle(newInvocationId, String::class.java).attachSuspend()
    assertThat(newInvocationResult.response).isEqualTo("$input from old")

    // Assert we both got invocation status completed
    await withAlias
        "got the two invocation status completed" untilAsserted
        {
          assertThat(getInvocationStatus(adminURI, initialInvocationId))
            .isEqualTo(
              SysInvocationEntry(id = initialInvocationId, status = "completed"),
            )
          assertThat(getInvocationStatus(adminURI, newInvocationId))
            .isEqualTo(
              SysInvocationEntry(id = newInvocationId, status = "completed"),
            )
        }
  }
}
