// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.runtime

import dev.restate.e2e.Containers
import dev.restate.e2e.utils.*
import dev.restate.sdk.client.Client
import java.util.concurrent.TimeUnit
import my.restate.e2e.services.CounterClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class PersistenceTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder().withServiceEndpoint(Containers.NODE_COUNTER_SERVICE_SPEC).build()
    }
  }

  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @Test
  fun startAndStopRuntimeRetainsTheState(
      @InjectClient ingressClient: Client,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeHandle: ContainerHandle
  ) {
    val counterClient = CounterClient.fromClient(ingressClient, "my-key")

    val res1 = counterClient.getAndAdd(1)
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Stop and start the runtime
    runtimeHandle.terminateAndRestart()

    val res2 = counterClient.getAndAdd(2)
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @Test
  fun startAndKillRuntimeRetainsTheState(
      @InjectClient ingressClient: Client,
      @InjectContainerHandle(RESTATE_RUNTIME) runtimeHandle: ContainerHandle
  ) {
    val counterClient = CounterClient.fromClient(ingressClient, "my-key")

    val res1 = counterClient.getAndAdd(1)
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    // Stop and start the runtime
    runtimeHandle.killAndRestart()

    val res2 = counterClient.getAndAdd(2)
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }
}
