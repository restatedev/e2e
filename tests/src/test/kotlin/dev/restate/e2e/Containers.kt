package dev.restate.e2e

import dev.restate.e2e.functions.collections.list.ListServiceGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.counter.CounterGrpc
import dev.restate.e2e.functions.counter.NoopGrpc
import dev.restate.e2e.functions.errors.FailingServiceGrpc
import dev.restate.e2e.functions.externalcall.RandomNumberListGeneratorGrpc
import dev.restate.e2e.functions.externalcall.ReplierGrpc
import dev.restate.e2e.functions.receiver.ReceiverGrpc
import dev.restate.e2e.functions.singletoncounter.SingletonCounterGrpc
import dev.restate.e2e.utils.FunctionSpec
import org.testcontainers.containers.GenericContainer

object Containers {

  // -- Generic containers and utils

  val EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC =
      "e2e-http-server" to
          GenericContainer("restatedev/e2e-http-server")
              .withEnv("PORT", "8080")
              .withExposedPorts(8080)

  fun getRestateEnvironment(): Map<String, String> {
    return System.getenv().filterKeys {
      (it.startsWith("RESTATE_") && it != "RESTATE_RUNTIME_CONTAINER") || it.startsWith("RUST_")
    }
  }

  // -- Java containers

  val JAVA_COLLECTIONS_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-java-services")
          .withEnv("SERVICES", listOf(ListServiceGrpc.SERVICE_NAME).joinToString(","))
          .withHostName("java-collections")
          .build()

  val JAVA_COUNTER_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-java-services")
          .withEnv(
              "SERVICES",
              listOf(
                      CounterGrpc.SERVICE_NAME,
                      NoopGrpc.SERVICE_NAME,
                      SingletonCounterGrpc.SERVICE_NAME)
                  .joinToString(","))
          .withHostName("java-counter")
          .build()

  val JAVA_COORDINATOR_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-java-services")
          .withEnv(
              "SERVICES",
              listOf(CoordinatorGrpc.SERVICE_NAME, ReceiverGrpc.SERVICE_NAME).joinToString(","))
          .withHostName("java-coordinator")
          .build()

  val JAVA_EXTERNALCALL_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-java-services")
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .withEnv(
              "SERVICES",
              listOf(ReplierGrpc.SERVICE_NAME, RandomNumberListGeneratorGrpc.SERVICE_NAME)
                  .joinToString(","))
          .withHostName("java-externalcall")
          .build()

  val JAVA_ERRORS_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-java-services")
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .withEnv("SERVICES", listOf(FailingServiceGrpc.SERVICE_NAME).joinToString(","))
          .withHostName("java-errors")
          .build()

  // -- Node containers

  val NODE_COUNTER_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-node-services")
          .withEnv("SERVICES", listOf(CounterGrpc.SERVICE_NAME).joinToString(","))
          .withHostName("node-counter")
          .build()
}
