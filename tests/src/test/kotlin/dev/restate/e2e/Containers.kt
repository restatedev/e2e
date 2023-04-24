package dev.restate.e2e

import dev.restate.e2e.utils.FunctionSpec
import org.testcontainers.containers.GenericContainer

object Containers {
  val COLLECTIONS_FUNCTION_SPEC = FunctionSpec.builder("restatedev/e2e-collections").build()

  val COUNTER_FUNCTION_SPEC = FunctionSpec.builder("restatedev/e2e-counter").build()

  val COORDINATOR_FUNCTION_SPEC = FunctionSpec.builder("restatedev/e2e-coordinator").build()

  val EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC =
      "e2e-http-server" to
          GenericContainer("restatedev/e2e-http-server")
              .withEnv("PORT", "8080")
              .withExposedPorts(8080)

  val EXTERNALCALL_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-externalcall")
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val ERRORS_FUNCTION_SPEC =
      FunctionSpec.builder("restatedev/e2e-errors")
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  fun getRestateEnvironment(): Map<String, String> {
    return System.getenv().filterKeys {
      (it.startsWith("RESTATE_") && it != "RESTATE_RUNTIME_CONTAINER") || it.startsWith("RUST_")
    }
  }
}
