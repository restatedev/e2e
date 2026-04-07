// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.client.Client
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.kotlin.awakeableHandle
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.resolve
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import java.util.UUID
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Serializable data class ResolveAwakeableRequest(val key: String, val value: String)

@VirtualObject
@Name("VersionedService")
class VersionedService(private val version: String) {
  @Handler suspend fun getVersion(): String = version

  @Handler
  suspend fun getVersionSuspendGetVersion(awakeableKey: String) {
    val results = state().get<List<String>>("results")?.toMutableList() ?: mutableListOf()
    results.add(version)

    val awk = awakeable<String>()
    state().set("awk_$awakeableKey", awk.id)
    state().set("results", results.toList())

    val awakeableResult = awk.await()

    val updatedResults = state().get<List<String>>("results")?.toMutableList() ?: mutableListOf()
    updatedResults.add(awakeableResult)
    updatedResults.add(version)
    state().set("results", updatedResults.toList())
  }

  @Shared
  suspend fun hasAwakeable(key: String): Boolean {
    return !state().get<String>("awk_$key").isNullOrEmpty()
  }

  @Shared
  suspend fun resolveAwakeable(request: ResolveAwakeableRequest) {
    val awkId =
        state().get<String>("awk_${request.key}")
            ?: throw TerminalException("No awakeable found for key ${request.key}")
    awakeableHandle(awkId).resolve(request.value)
  }

  @Shared
  open suspend fun getResults(): List<String> {
    return state().get<List<String>>("results") ?: emptyList()
  }
}

@Tag("always-suspending")
@Tag("only-single-node")
class UpgradeWithNewInvocation {

  companion object {
    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(VersionedService("v1")))
    }
  }

  @Test
  fun executesNewInvocationWithLatestServiceRevisions(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    val interpreter = ingressClient.toVirtualObject<VersionedService>(UUID.randomUUID().toString())

    // Execute the first request
    val firstResult =
        interpreter.request { getVersion() }.options(idempotentCallOptions).call().response
    assertThat(firstResult).isEqualTo("v1")

    // Now register the update
    startAndRegisterLocalEndpoint(Endpoint.bind(VersionedService("v2")).build(), adminURI)

    // After the update, the runtime might not immediately propagate the usage of the new version.
    // For this reason, we try to invoke several times until we see the new version running.
    await withAlias
        "should now use service v2" untilAsserted
        {
          assertThat(
                  interpreter
                      .request { getVersion() }
                      .options(idempotentCallOptions)
                      .call()
                      .response)
              .isEqualTo("v2")
        }
  }
}

@Tag("always-suspending")
@Tag("only-single-node")
class UpgradeWithInFlightInvocation {

  companion object {
    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(VersionedService("v1")))
    }
  }

  @Test
  fun inFlightInvocation(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    val interpreter = ingressClient.toVirtualObject<VersionedService>(UUID.randomUUID().toString())
    val awakeableKey = "upgrade"

    // Start an invocation that will: record v1, create awakeable and wait, then record v1 again
    interpreter
        .request { getVersionSuspendGetVersion(awakeableKey) }
        .options(idempotentCallOptions)
        .send()

    // Await until awakeable is registered
    await withAlias
        "reach sync point" untilAsserted
        {
          assertThat(interpreter.request { hasAwakeable(awakeableKey) }.call().response).isTrue()
        }

    // Now register the update
    startAndRegisterLocalEndpoint(Endpoint.bind(VersionedService("v2")).build(), adminURI)

    // Now let's resume the awakeable
    interpreter
        .request { resolveAwakeable(ResolveAwakeableRequest(awakeableKey, "unlocked")) }
        .options(idempotentCallOptions)
        .call()

    // The in-flight invocation should have used v1 for both version checks
    await withAlias
        "old invocation remains on v1" untilAsserted
        {
          assertThat(interpreter.request { getResults() }.call().response)
              .containsExactly("v1", "unlocked", "v1")
        }

    // New invocations on a different key should use v2
    val newInterpreter =
        ingressClient.toVirtualObject<VersionedService>(UUID.randomUUID().toString())
    await withAlias
        "new invocations should use service v2" untilAsserted
        {
          assertThat(
                  newInterpreter
                      .request { getVersion() }
                      .options(idempotentCallOptions)
                      .call()
                      .response)
              .isEqualTo("v2")
        }
  }
}
