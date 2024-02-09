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
import dev.restate.e2e.services.collections.map.MapServiceGrpc
import dev.restate.e2e.services.collections.map.MapServiceGrpc.MapServiceBlockingStub
import dev.restate.e2e.services.collections.map.clearAllRequest
import dev.restate.e2e.services.collections.map.getRequest
import dev.restate.e2e.services.collections.map.setRequest
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.CounterProto
import dev.restate.e2e.services.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.services.counter.ProxyCounterGrpc
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.util.UUID
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
                        CounterGrpc.SERVICE_NAME,
                        ProxyCounterGrpc.SERVICE_NAME,
                        SingletonCounterGrpc.SERVICE_NAME,
                        MapServiceGrpc.SERVICE_NAME))
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
                            CounterGrpc.SERVICE_NAME,
                            ProxyCounterGrpc.SERVICE_NAME,
                            MapServiceGrpc.SERVICE_NAME)
                        .build())
                .build())
  }
}

abstract class BaseStateTest {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun add(@InjectBlockingStub counterClient: CounterBlockingStub) {
    counterClient.add(
        CounterAddRequest.newBuilder().setCounterName("noReturnValue").setValue(1).build())
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun getAndSet(@InjectBlockingStub counterClient: CounterBlockingStub) {
    val res1 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(1).build())
    assertThat(res1.oldValue).isEqualTo(0)
    assertThat(res1.newValue).isEqualTo(1)

    val res2 =
        counterClient.getAndAdd(
            CounterAddRequest.newBuilder().setCounterName("my-key").setValue(2).build())
    assertThat(res2.oldValue).isEqualTo(1)
    assertThat(res2.newValue).isEqualTo(3)
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun setStateViaOneWayCallFromAnotherService(
      @InjectBlockingStub proxyCounter: ProxyCounterGrpc.ProxyCounterBlockingStub,
      @InjectBlockingStub counterClient: CounterBlockingStub
  ) {
    val counterName = "setStateViaOneWayCallFromAnotherService"
    val counterRequest =
        CounterAddRequest.newBuilder().setCounterName(counterName).setValue(1).build()

    proxyCounter.addInBackground(counterRequest)
    proxyCounter.addInBackground(counterRequest)
    proxyCounter.addInBackground(counterRequest)

    await untilCallTo
        {
          counterClient.get(
              CounterProto.CounterRequest.newBuilder().setCounterName(counterName).build())
        } matches
        { num ->
          num!!.value == 3L
        }
  }

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun listStateAndClearAll(@InjectBlockingStub mapService: MapServiceBlockingStub) {
    val mapName = UUID.randomUUID().toString()

    mapService.set(
        setRequest {
          this.mapName = mapName
          this.key = "my-key-0"
          this.value = "my-value-0"
        })
    mapService.set(
        setRequest {
          this.mapName = mapName
          this.key = "my-key-1"
          this.value = "my-value-1"
        })

    // Set state to another map
    mapService.set(
        setRequest {
          this.mapName = mapName + "1"
          this.key = "my-key-2"
          this.value = "my-value-2"
        })

    // Clear all
    val clearResponse = mapService.clearAll(clearAllRequest { this.mapName = mapName })
    assertThat(clearResponse.keysList).containsExactlyInAnyOrder("my-key-0", "my-key-1")

    // Check keys are not available
    assertThat(
            mapService
                .get(
                    getRequest {
                      this.mapName = mapName
                      key = "my-key-0"
                    })
                .value)
        .isEmpty()
    assertThat(
            mapService
                .get(
                    getRequest {
                      this.mapName = mapName
                      key = "my-key-1"
                    })
                .value)
        .isEmpty()

    // Check the other service instance was left untouched
    assertThat(
            mapService
                .get(
                    getRequest {
                      this.mapName = mapName + "1"
                      this.key = "my-key-2"
                    })
                .value)
        .isEqualTo("my-value-2")
  }
}
