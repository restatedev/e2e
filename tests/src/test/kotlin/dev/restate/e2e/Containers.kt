package dev.restate.e2e

import dev.restate.e2e.utils.RestateDeployer
import org.testcontainers.containers.GenericContainer

object Containers {
  val COUNTER_CONTAINER_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-counter", arrayOf("counter.Counter", "counter.Noop"), mapOf())

  val COORDINATOR_CONTAINER_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-coordinator", arrayOf("coordinator.Coordinator", "coordinator.Receiver"), mapOf())

  val EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC =
      "e2e-http-server" to
          GenericContainer("restatedev/e2e-http-server")
              .withEnv("PORT", "8080")
              .withExposedPorts(8080)

  val EXTERNALCALL_CONTAINER_SPEC =
      RestateDeployer.FunctionSpec(
          "e2e-externalcall",
          arrayOf(
              "restate.e2e.externalcall.rnlg.RandomNumberListGenerator",
              "restate.e2e.externalcall.replier.Replier"),
          mapOf(
              "HTTP_SERVER_ADDRESS" to
                  "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080"))
}
