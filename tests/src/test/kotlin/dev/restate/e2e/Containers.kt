// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.ServiceSpec
import my.restate.e2e.services.*
import org.testcontainers.containers.GenericContainer

object Containers {

  // -- Generic containers and utils

  const val INT_SORTER_HTTP_SERVER_HOSTNAME = "e2e-http-server"

  fun intSorterHttpServerContainer() =
      GenericContainer("restatedev/e2e-http-server").withEnv("PORT", "8080").withExposedPorts(8080)

  val INT_SORTER_HTTP_SERVER_CONTAINER_SPEC =
      INT_SORTER_HTTP_SERVER_HOSTNAME to intSorterHttpServerContainer()

  // -- Java containers

  fun javaServicesContainer(hostName: String, vararg services: String): ServiceSpec.Builder {
    assert(services.isNotEmpty())
    return ServiceSpec.builder("restatedev/e2e-java-services")
        .withEnv("SERVICES", services.joinToString(","))
        .withHostName(hostName)
  }

  val JAVA_COLLECTIONS_SERVICE_SPEC =
      javaServicesContainer("java-collections", ListObjectDefinitions.SERVICE_NAME).build()

  val JAVA_COUNTER_SERVICE_SPEC =
      javaServicesContainer(
              "java-counter", CounterDefinitions.SERVICE_NAME, ProxyCounterDefinitions.SERVICE_NAME)
          .build()

  val JAVA_COORDINATOR_SERVICE_SPEC =
      javaServicesContainer(
              "java-coordinator",
              CoordinatorDefinitions.SERVICE_NAME,
              ReceiverDefinitions.SERVICE_NAME)
          .build()

  val JAVA_EXTERNALCALL_SERVICE_SPEC =
      javaServicesContainer("java-externalcall", RandomNumberListGeneratorDefinitions.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${INT_SORTER_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val JAVA_ERRORS_SERVICE_SPEC =
      javaServicesContainer("java-errors", FailingDefinitions.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${INT_SORTER_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val JAVA_WORKFLOW_SERVICE_SPEC =
      javaServicesContainer("java-workflow", WorkflowAPIBlockAndWaitDefinitions.SERVICE_NAME)
          .build()

  // -- Node containers

  fun nodeServicesContainer(hostName: String, vararg services: String): ServiceSpec.Builder {
    assert(services.isNotEmpty())
    return ServiceSpec.builder("restatedev/e2e-node-services")
        .withEnv("SERVICES", services.joinToString(","))
        .withEnv("RESTATE_LOGGING", "TRACE")
        .withHostName(hostName)
  }

  val NODE_COUNTER_SERVICE_SPEC =
      nodeServicesContainer(
              "node-counter", CounterDefinitions.SERVICE_NAME, ProxyCounterDefinitions.SERVICE_NAME)
          .build()

  val NODE_COORDINATOR_SERVICE_SPEC =
      nodeServicesContainer(
              "node-coordinator",
              CoordinatorDefinitions.SERVICE_NAME,
              ReceiverDefinitions.SERVICE_NAME,
              CoordinatorDefinitions.SERVICE_NAME)
          .build()

  val NODE_COLLECTIONS_SERVICE_SPEC =
      nodeServicesContainer("node-collections", ListObjectDefinitions.SERVICE_NAME).build()

  val NODE_EXTERNALCALL_SERVICE_SPEC =
      nodeServicesContainer("node-externalcall", RandomNumberListGeneratorDefinitions.SERVICE_NAME)
          .withEnv(
              "HTTP_SERVER_ADDRESS", "http://${INT_SORTER_HTTP_SERVER_CONTAINER_SPEC.first}:8080")
          .build()

  val NODE_ERRORS_SERVICE_SPEC =
      nodeServicesContainer("node-errors", FailingDefinitions.SERVICE_NAME).build()

  const val WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME = "WorkflowAPIBlockAndWait"
  val NODE_WORKFLOW_SERVICE_SPEC =
      nodeServicesContainer("node-workflow", WORKFLOW_API_BLOCK_AND_WAIT_SERVICE_NAME).build()

  // --- Kotlin containers

  fun kotlinServicesContainer(hostName: String, vararg services: String): ServiceSpec.Builder {
    assert(services.isNotEmpty())
    return ServiceSpec.builder("restatedev/e2e-kotlin-services")
        .withEnv("SERVICES", services.joinToString(","))
        .withHostName(hostName)
  }
}
