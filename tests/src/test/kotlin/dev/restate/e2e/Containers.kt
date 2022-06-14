package dev.restate.e2e

import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.NoopGrpc
import dev.restate.e2e.functions.externalcall.RandomNumberListGeneratorGrpc
import dev.restate.e2e.functions.externalcall.ReplierGrpc
import dev.restate.e2e.functions.receiver.ReceiverGrpc
import dev.restate.e2e.utils.FunctionSpec
import org.testcontainers.containers.GenericContainer

object Containers {
  val COUNTER_FUNCTION_SPEC =
      FunctionSpec.builder(
              "restatedev/e2e-counter", CounterGrpc.SERVICE_NAME, NoopGrpc.SERVICE_NAME)
          .build()

  val COORDINATOR_FUNCTION_SPEC =
      FunctionSpec.builder(
              "restatedev/e2e-coordinator", CoordinatorGrpc.SERVICE_NAME, ReceiverGrpc.SERVICE_NAME)
          .build()

  val EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC =
      "e2e-http-server" to
          GenericContainer("restatedev/e2e-http-server")
              .withEnv("PORT", "8080")
              .withExposedPorts(8080)

  val EXTERNALCALL_FUNCTION_SPEC =
      FunctionSpec.builder(
              "restatedev/e2e-externalcall",
              RandomNumberListGeneratorGrpc.SERVICE_NAME,
              ReplierGrpc.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()
}
