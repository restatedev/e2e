// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.Containers.javaServicesContainer
import dev.restate.e2e.utils.InjectClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.Client
import java.util.*
import my.restate.e2e.services.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Tag("always-suspending")
@Tag("lazy-state")
class JavaStateTest : BaseStateTest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    javaServicesContainer(
                        "java-counter",
                        CounterDefinitions.SERVICE_NAME,
                        ProxyCounterDefinitions.SERVICE_NAME,
                        MapObjectDefinitions.SERVICE_NAME))
                .build())
  }
}

@Tag("always-suspending")
@Tag("lazy-state")
class NodeStateTest : BaseStateTest() {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withServiceEndpoint(
                    Containers.nodeServicesContainer(
                        "node-counter",
                        CounterDefinitions.SERVICE_NAME,
                        ProxyCounterDefinitions.SERVICE_NAME,
                        MapObjectDefinitions.SERVICE_NAME))
                .build())
  }
}

abstract class BaseStateTest {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun add(@InjectClient ingressClient: Client) {
    CounterClient.fromClient(ingressClient, "noReturnValue").add(1)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun getAndSet(@InjectClient ingressClient: Client) {
    val counterClient = CounterClient.fromClient(ingressClient, "getAndSet")
    val res1 = counterClient.getAndAdd(1)
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    val res2 = counterClient.getAndAdd(2)
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun setStateViaOneWayCallFromAnotherService(@InjectClient ingressClient: Client) {
    val counterName = "setStateViaOneWayCallFromAnotherService"
    val proxyCounter = ProxyCounterClient.fromClient(ingressClient)

    proxyCounter.addInBackground(ProxyCounter.AddRequest(counterName, 1))
    proxyCounter.addInBackground(ProxyCounter.AddRequest(counterName, 1))
    proxyCounter.addInBackground(ProxyCounter.AddRequest(counterName, 1))

    await untilCallTo
        {
          CounterClient.fromClient(ingressClient, counterName).get()
        } matches
        { num ->
          num!! == 3L
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun listStateAndClearAll(@InjectClient ingressClient: Client) {
    val mapName = UUID.randomUUID().toString()
    val mapObj = MapObjectClient.fromClient(ingressClient, mapName)
    val anotherMapObj = MapObjectClient.fromClient(ingressClient, mapName + "1")

    mapObj.set(MapObject.Entry("my-key-0", "my-value-0"))
    mapObj.set(MapObject.Entry("my-key-1", "my-value-1"))

    // Set state to another map
    anotherMapObj.set(MapObject.Entry("my-key-2", "my-value-2"))

    // Clear all
    assertThat(mapObj.clearAll()).containsExactlyInAnyOrder("my-key-0", "my-key-1")

    // Check keys are not available
    assertThat(mapObj.get("my-key-0")).isEmpty()
    assertThat(mapObj.get("my-key-1")).isEmpty()

    // Check the other service instance was left untouched
    assertThat(anotherMapObj.get("my-key-2")).isEqualTo("my-value-2")
  }
}
