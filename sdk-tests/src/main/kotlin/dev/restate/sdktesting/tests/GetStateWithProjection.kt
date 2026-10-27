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
import dev.restate.client.kotlin.*
import dev.restate.sdktesting.contracts.MapObject
import dev.restate.sdktesting.contracts.MapObject.Entry
import dev.restate.sdktesting.contracts.MapObject.ProjectRequest
import dev.restate.sdktesting.infra.*
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Get state with a projection. Runs both with eager state (default suite) and lazy state (lazyState
 * suite).
 */
class GetStateWithProjection {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(ServiceSpec.defaultBuilder().withServices(MapObject::class))
      // Getting state without recording it in the journal requires invocation protocol v8.
      withEnv("RESTATE_EXPERIMENTAL_ENABLE_PROTOCOL_V8", "true")
    }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun valuePresent(@InjectClient ingressClient: Client) = runTest {
    val mapObj = ingressClient.toVirtualObject<MapObject>(UUID.randomUUID().toString())
    mapObj
        .request { set(Entry("my-key", """{"a":{"b":"my-value"}}""")) }
        .options(idempotentCallOptions)
        .call()

    assertThat(
            mapObj
                .request { getProject(ProjectRequest("my-key", "/a/b")) }
                .options(idempotentCallOptions)
                .call()
                .response
        )
        .isEqualTo("\"my-value\"")
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun valueAbsent(@InjectClient ingressClient: Client) = runTest {
    val mapObj = ingressClient.toVirtualObject<MapObject>(UUID.randomUUID().toString())

    assertThat(
            mapObj
                .request { getProject(ProjectRequest("my-key", "/a/b")) }
                .options(idempotentCallOptions)
                .call()
                .response
        )
        .isEmpty()
  }
}
