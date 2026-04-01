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
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import kotlinx.serialization.json.Json
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
    suspend fun setState(value: String) {
      state().set("state", value)
    }

    @Handler
    suspend fun getState(): String? {
      return state().get<String>("state")
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
    val client = ingressClient.toVirtualObject<StateObject>("test-key")

    // Set initial state
    val initialState = "initial-state"
    client.request { setState(initialState) }.options(idempotentCallOptions).call()

    // Verify initial state
    assertThat(client.request { getState() }.options(idempotentCallOptions).call().response)
        .isEqualTo(initialState)

    // Create admin client for state patching
    val serviceApi = ServiceApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))

    // Patch the state
    val newState = "patched-state"
    val request =
        ModifyServiceStateRequest()
            .objectKey("test-key")
            .newState(
                mapOf("state" to Json.encodeToString(newState).toByteArray().map { it.toInt() }))

    await withAlias
        "modify service state" untilAsserted
        {
          serviceApi.modifyServiceState("StateObject", request)
        }

    // Verify patched state
    await withAlias
        "state is patched" untilAsserted
        {
          assertThat(client.request { getState() }.call().response).isEqualTo(newState)
        }
  }
}
