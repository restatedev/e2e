// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.services.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.services.counter.CounterProto
import dev.restate.e2e.services.counter.CounterProto.CounterAddRequest
import dev.restate.e2e.services.counter.ProxyCounterGrpc
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.JAVA_COUNTER_SERVICE_SPEC)
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
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(Containers.NODE_COUNTER_SERVICE_SPEC)
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
}
