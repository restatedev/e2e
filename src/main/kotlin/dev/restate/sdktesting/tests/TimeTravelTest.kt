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
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TimeTravelTest {

  @VirtualObject
  @Name("TimeObject")
  class TimeObject {
    companion object {
      val shouldFail = AtomicBoolean(true)
    }

    @Handler
    suspend fun getState(ctx: ObjectContext): String? {
      return ctx.get<String>("state")
    }

    @Handler
    suspend fun testHandler(ctx: ObjectContext): String {
      // Call another service, but don't await the response yet
      val firstMethodResponse = TimeTravelTestCalleeServiceClient.fromContext(ctx).firstMethod()

      // Load if we should fail
      val shouldFail = shouldFail.get()

      // Set a different state
      ctx.set("state", if (shouldFail) "a" else "b")

      // Execute a different context run
      val runResult = ctx.runAsync("my-run") { if (shouldFail) "a" else "b" }

      // Call the second method
      val secondMethodResponse =
          TimeTravelTestCalleeServiceClient.fromContext(ctx).secondMethod(shouldFail)

      // Await all calls and return
      return listOf(firstMethodResponse, secondMethodResponse, runResult)
          .awaitAll()
          .joinToString("-")
    }
  }

  @Service
  @Name("CalleeService")
  class CalleeService {
    companion object {
      val firstMethod = AtomicInteger(0)
    }

    @Handler
    suspend fun firstMethod(ctx: Context): String {
      ctx.sleep(2.seconds)
      // Assert it's called just once
      assertThat(firstMethod.incrementAndGet()).isEqualTo(1)
      return "first"
    }

    @Handler
    fun secondMethod(ctx: Context, shouldFail: Boolean): String {
      if (shouldFail) {
        throw RuntimeException("Failing!")
      }
      return "second"
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(TimeObject()).bind(CalleeService()))
    }
  }

  @Test
  fun testTimeTravel(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    // Create clients for the services
    val timeClient = TimeTravelTestTimeObjectClient.fromClient(ingressClient, "test-key")

    // Send request
    val sendResult = timeClient.send().testHandler(init = idempotentCallOptions)
    val invocationId = sendResult.invocationId()

    // Wait for the invocation to reach the error state
    await withAlias
        "invocation has all the journal entries" untilAsserted
        {
          assertThat(getJournal(adminURI, invocationId).rows)
              .map<String> { it.entryType }
              .containsExactlyInAnyOrder(
                  "Command: Input",
                  "Command: Call",
                  "Notification: CallInvocationId",
                  "Notification: Call",
                  "Command: SetState",
                  "Command: Run",
                  "Notification: Run",
                  "Command: Call",
                  "Notification: CallInvocationId",
              )
        }

    // Find the trim index
    val trimIndex =
        getJournal(adminURI, invocationId).rows.find { it.entryType == "Command: SetState" }!!.index

    // Set shouldFail to false so the handler will succeed after time travel
    TimeObject.shouldFail.set(false)

    // Use the time travel API to trim entry index 2
    val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
    val invocationApi = InvocationApi(adminClient)
    invocationApi.timeTravelInvocation(invocationId, trimIndex)

    // Wait for the response to be sent back
    assertThat(sendResult.attachSuspend().response()).isEqualTo("first-second-b")

    // Wait for state to be b
    await withAlias
        "response is sent back" untilAsserted
        {
          assertThat(timeClient.getState()).isEqualTo("b")
        }
  }
}
