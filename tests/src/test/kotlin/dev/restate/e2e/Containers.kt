package dev.restate.e2e

import dev.restate.e2e.utils.RestateDeployer

object Containers {
  val COUNTER_CONTAINER_SPEC =
      RestateDeployer.FunctionSpec("e2e-counter", arrayOf("counter.Counter", "counter.Noop"))

  val COORDINATOR_CONTAINER_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-coordinator", arrayOf("coordinator.Coordinator", "coordinator.Receiver"))
}
