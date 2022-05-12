package dev.restate.e2e

import dev.restate.e2e.functions.counter.CounterGrpc.CounterBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.e2e.utils.RestateDeployerExtension.InjectBlockingStub
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CounterTest {

    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension(
        RestateDeployer.Builder().function("e2e-counter").build()
    )

    @Test
    fun test(@InjectBlockingStub("e2e-counter") counterClient: CounterBlockingStub) {
        counterClient.add(dev.restate.e2e.functions.counter.Number.newBuilder().setValue(1).build())
    }

}