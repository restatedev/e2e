package dev.restate.e2e

import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.NoopGrpc
import dev.restate.e2e.functions.externalcall.RandomNumberListGeneratorGrpc
import dev.restate.e2e.functions.externalcall.ReplierGrpc
import dev.restate.e2e.utils.RestateDeployer
import org.testcontainers.containers.GenericContainer

object Containers {
  val COUNTER_FUNCTION_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-counter", arrayOf(CounterGrpc.SERVICE_NAME, NoopGrpc.SERVICE_NAME), mapOf())

  val COORDINATOR_FUNCTION_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-coordinator",
          arrayOf(CoordinatorGrpc.SERVICE_NAME, CoordinatorGrpc.SERVICE_NAME),
          mapOf())

  val EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC =
      "e2e-http-server" to
          GenericContainer("restatedev/e2e-http-server")
              .withEnv("PORT", "8080")
              .withExposedPorts(8080)

  val EXTERNALCALL_FUNCTION_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-externalcall",
          arrayOf(RandomNumberListGeneratorGrpc.SERVICE_NAME, ReplierGrpc.SERVICE_NAME),
          mapOf(
              "HTTP_SERVER_ADDRESS" to
                  "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080"))
}
