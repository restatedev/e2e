package dev.restate.e2e

import dev.restate.e2e.services.collections.list.ListServiceGrpc
import dev.restate.e2e.services.coordinator.CoordinatorGrpc
import dev.restate.e2e.services.counter.CounterGrpc
import dev.restate.e2e.services.counter.ProxyCounterGrpc
import dev.restate.e2e.services.errors.FailingServiceGrpc
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorGrpc
import dev.restate.e2e.services.externalcall.ReplierGrpc
import dev.restate.e2e.services.receiver.ReceiverGrpc
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc
import dev.restate.e2e.services.verification.interpreter.CommandInterpreterGrpc
import dev.restate.e2e.services.verification.verifier.CommandVerifierGrpc
import dev.restate.e2e.utils.ServiceSpec
import dev.restate.e2e.utils.ServiceSpec.RegistrationOptions
import dev.restate.e2e.utils.ServiceSpec.RetryPolicy
import org.testcontainers.containers.GenericContainer

object Containers {

  // -- Generic containers and utils

  val EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC =
      "e2e-http-server" to
          GenericContainer("restatedev/e2e-http-server")
              .withEnv("PORT", "8080")
              .withExposedPorts(8080)

  val FIXED_DELAY_RETRY_POLICY = RetryPolicy.FixedDelay("1s", 20)

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
      javaServicesContainer(
              "java-externalcall",
              ReplierGrpc.SERVICE_NAME,
              RandomNumberListGeneratorGrpc.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val JAVA_ERRORS_SERVICE_SPEC =
      javaServicesContainer("java-errors", FailingServiceGrpc.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${EXTERNALCALL_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .withRegistrationOptions(RegistrationOptions(retryPolicy = RetryPolicy.None))
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

  val NODE_COLLECTIONS_SERVICE_SPEC =
      nodeServicesContainer("node-collections", ListServiceGrpc.SERVICE_NAME)

  val NODE_ERRORS_SERVICE_SPEC =
      nodeServicesContainer("node-errors", FailingServiceGrpc.SERVICE_NAME)
          .withRegistrationOptions(RegistrationOptions(retryPolicy = RetryPolicy.None))

  // -- Verification test container

  const val VERIFICATION_SERVICE_HOSTNAME = "restate-verification"

  val VERIFICATION_SERVICE_SPEC =
      nodeServicesContainer(
              VERIFICATION_SERVICE_HOSTNAME,
              CommandVerifierGrpc.SERVICE_NAME,
              CommandInterpreterGrpc.SERVICE_NAME)
          .withRegistrationOptions(RegistrationOptions(retryPolicy = FIXED_DELAY_RETRY_POLICY))
}
