// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.ServiceApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.ModifyServiceStateRequest
import dev.restate.client.Client
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.set
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import kotlinx.serialization.json.Json
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class StatePatchingTest {

  @VirtualObject
  @Name("StateObject")
  class StateObject {
    @Handler
    suspend fun setState(ctx: ObjectContext, value: String) {
      ctx.set("state", value)
    }

    @Handler
    suspend fun getState(ctx: ObjectContext): String? {
      return ctx.get<String>("state")
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(StateObject()))
    }
  }

  @Test
  fun shouldPatchState(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    // Create a client for the StateObject
    val client = StatePatchingTestStateObjectClient.fromClient(ingressClient, "test-key")

    // Set initial state
    val initialState = "initial-state"
    client.setState(initialState)

    // Verify initial state
    assertThat(client.getState()).isEqualTo(initialState)

    // Create admin client for state patching
    val serviceApi = ServiceApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))

    // Patch the state
    val newState = "patched-state"
    val request = ModifyServiceStateRequest()
        .objectKey("test-key")
        .newState(mapOf("state" to Json.encodeToString(newState).toByteArray().map { it.toInt() }))

    serviceApi.modifyServiceState("StateObject", request)

    // Verify patched state
    await withAlias
        "state is patched" untilAsserted
        {
          assertThat(client.getState()).isEqualTo(newState)
        }
  }
}
