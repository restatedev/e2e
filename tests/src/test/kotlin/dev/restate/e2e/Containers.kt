package dev.restate.e2e

import dev.restate.e2e.services.collections.list.ListServiceGrpc
import dev.restate.e2e.services.coordinator.CoordinatorGrpc
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.ProxyCounterGrpc
import dev.restate.e2e.services.errors.FailingServiceGrpc
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorGrpc
import dev.restate.e2e.services.proxy.ProxyServiceGrpc
import dev.restate.e2e.services.receiver.ReceiverGrpc
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc
import dev.restate.e2e.services.verification.interpreter.CommandInterpreterGrpc
import dev.restate.e2e.services.verification.verifier.CommandVerifierGrpc
import dev.restate.e2e.utils.ServiceSpec
import org.testcontainers.containers.GenericContainer

object Containers {

  // -- Generic containers and utils

  const val INT_SORTER_HTTP_SERVER_HOSTNAME = "e2e-http-server"
  fun intSorterHttpServerContainer() =
      GenericContainer("restatedev/e2e-http-server").withEnv("PORT", "8080").withExposedPorts(8080)

  val INT_SORTER_HTTP_SERVER_CONTAINER_SPEC =
      INT_SORTER_HTTP_SERVER_HOSTNAME to intSorterHttpServerContainer()

  fun getRestateEnvironment(): Map<String, String> {
    return System.getenv().filterKeys {
      (it.uppercase().startsWith("RESTATE_") && it.uppercase() != "RESTATE_RUNTIME_CONTAINER") ||
          it.uppercase().startsWith("RUST_")
    }
  }

  // -- Java containers

  fun javaServicesContainer(hostName: String, vararg services: String): ServiceSpec.Builder {
    assert(services.isNotEmpty())
    return ServiceSpec.builder("restatedev/e2e-java-services")
        .withEnv("SERVICES", services.joinToString(","))
        .withHostName(hostName)
  }

  val JAVA_COLLECTIONS_SERVICE_SPEC =
      javaServicesContainer("java-collections", ListServiceGrpc.SERVICE_NAME).build()

  val JAVA_COUNTER_SERVICE_SPEC =
      javaServicesContainer(
              "java-counter",
              CounterGrpc.SERVICE_NAME,
              ProxyCounterGrpc.SERVICE_NAME,
              SingletonCounterGrpc.SERVICE_NAME)
          .build()

  val JAVA_COORDINATOR_SERVICE_SPEC =
      javaServicesContainer(
              "java-coordinator", CoordinatorGrpc.SERVICE_NAME, ReceiverGrpc.SERVICE_NAME)
          .build()

  val JAVA_EXTERNALCALL_SERVICE_SPEC =
      javaServicesContainer("java-externalcall", RandomNumberListGeneratorGrpc.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${INT_SORTER_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val JAVA_ERRORS_SERVICE_SPEC =
      javaServicesContainer("java-errors", FailingServiceGrpc.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${INT_SORTER_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  // -- Node containers

  fun nodeServicesContainer(hostName: String, vararg services: String): ServiceSpec.Builder {
    assert(services.isNotEmpty())
    return ServiceSpec.builder("restatedev/e2e-node-services")
        .withEnv("SERVICES", services.joinToString(","))
        .withEnv("RESTATE_DEBUG_LOGGING", "JOURNAL")
        .withHostName(hostName)
  }

  val NODE_COUNTER_SERVICE_SPEC =
      nodeServicesContainer("node-counter", CounterGrpc.SERVICE_NAME, ProxyCounterGrpc.SERVICE_NAME)
          .build()

  val NODE_COORDINATOR_SERVICE_SPEC =
      nodeServicesContainer(
              "node-coordinator", CoordinatorGrpc.SERVICE_NAME, ReceiverGrpc.SERVICE_NAME)
          .build()

  val NODE_COLLECTIONS_SERVICE_SPEC =
      nodeServicesContainer("node-collections", ListServiceGrpc.SERVICE_NAME).build()

  val NODE_EXTERNALCALL_SERVICE_SPEC =
      nodeServicesContainer("node-externalcall", RandomNumberListGeneratorGrpc.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${INT_SORTER_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val NODE_ERRORS_SERVICE_SPEC =
      nodeServicesContainer("node-errors", FailingServiceGrpc.SERVICE_NAME).build()

  val NODE_PROXY_SERVICE_SPEC =
      nodeServicesContainer("node-proxy", ProxyServiceGrpc.SERVICE_NAME).build()

  const val HANDLER_API_ECHO_TEST_SERVICE_NAME = "handlerapi.HandlerAPIEchoTest"
  const val HANDLER_API_COUNTER_SERVICE_NAME = "handlerapi.Counter"

  val NODE_HANDLER_API_ECHO_TEST_SERVICE_SPEC =
      nodeServicesContainer("node-proxy", HANDLER_API_ECHO_TEST_SERVICE_NAME).build()

  const val EMBEDDED_HANDLER_SERVER_HOSTNAME = "node-embedded-handler"
  const val EMBEDDED_HANDLER_SERVER_PORT = 8080
  fun embeddedHandlerServerContainer() =
      GenericContainer("restatedev/e2e-node-services")
          .withEnv("EMBEDDED_HANDLER_PORT", EMBEDDED_HANDLER_SERVER_PORT.toString())
          .withExposedPorts(EMBEDDED_HANDLER_SERVER_PORT)

  val EMBEDDED_HANDLER_SERVER_CONTAINER_SPEC =
      EMBEDDED_HANDLER_SERVER_HOSTNAME to embeddedHandlerServerContainer()

  // -- Verification test container

  const val VERIFICATION_SERVICE_HOSTNAME = "restate-verification"

  val VERIFICATION_SERVICE_SPEC =
      nodeServicesContainer(
              VERIFICATION_SERVICE_HOSTNAME,
              CommandVerifierGrpc.SERVICE_NAME,
              CommandInterpreterGrpc.SERVICE_NAME)
          .build()
}
