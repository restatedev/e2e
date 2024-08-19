// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":test-utils"))
  testImplementation(project(":contracts"))
  testImplementation(project(":contracts-kt"))

  testImplementation(libs.restate.admin)

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")

  testImplementation(libs.log4j.api)
  testRuntimeOnly(libs.log4j.core)
  testRuntimeOnly(libs.log4j.slf4j)

  testImplementation(libs.jackson.core)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.jackson.parameter.names)

  testImplementation("org.apache.kafka:kafka-clients:3.5.0")

  testImplementation(libs.kotlinx.serialization.core)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.ktor.client.core)
  testImplementation(libs.ktor.client.java)
  testImplementation(libs.ktor.client.content.negotiation)
  testImplementation(libs.ktor.serialization.kotlinx.json)

  testImplementation(libs.awaitility)
}

tasks {
  val defaultLogFilters =
      mapOf(
          "restate_invoker" to "trace",
          "restate_ingress_kafka" to "trace",
          "restate_worker::partition::services::non_deterministic::remote_context" to "trace",
          "restate" to "debug")
  val defaultLog =
      (listOf("info") + defaultLogFilters.map { "${it.key}=${it.value}" }).joinToString(
          separator = ",")

  fun baseRestateEnvironment(name: String) =
      mapOf(
          "CONTAINER_LOGS_DIR" to
              layout.buildDirectory
                  .dir("test-results/$name/container-logs")
                  .get()
                  .asFile
                  .absolutePath,
          "RESTATE_CONTAINER_IMAGE" to
              (if (System.getenv("RESTATE_CONTAINER_IMAGE").isNullOrEmpty())
                  "ghcr.io/restatedev/restate:main"
              else System.getenv("RESTATE_CONTAINER_IMAGE")),
          "RUST_LOG" to (System.getenv("RUST_LOG") ?: defaultLog),
          "RUST_BACKTRACE" to "full")

  test {
    environment = environment + baseRestateEnvironment(name)

    useJUnitPlatform {
      // Run all the tests with either no tags, or always-suspending tag
      includeTags("none() | always-suspending")
    }
  }

  val testAlwaysSuspending by
      registering(Test::class) {
        environment =
            environment +
                baseRestateEnvironment(name) +
                mapOf("RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s")

        useJUnitPlatform {
          // Run all the tests with always-suspending or only-always-suspending tag
          includeTags("always-suspending | only-always-suspending")
        }
        // Increase a bit the default timeout
        systemProperties["junit.jupiter.execution.timeout.testable.method.default"] = "30 s"
      }

  val testSingleThreadSinglePartition by
      registering(Test::class) {
        environment =
            environment +
                baseRestateEnvironment(name) +
                mapOf(
                    "RESTATE_WORKER__BOOTSTRAP_NUM_PARTITIONS" to "1",
                    "RESTATE_DEFAULT_THREAD_POOL_SIZE" to "1",
                )

        useJUnitPlatform {
          // Run all the tests with either no tags, or always-suspending tag
          includeTags("none() | always-suspending")
        }
        // Increase a bit the default timeout
        systemProperties["junit.jupiter.execution.timeout.testable.method.default"] = "30 s"
      }

  // Common configuration for all test tasks
  withType<Test>().configureEach {
    dependsOn(":services:http-server:jibDockerBuild")
    dependsOn(":services:java-services:jibDockerBuild")
    dependsOn(":services:kotlin-services:jibDockerBuild")
    dependsOn(":services:node-services:dockerBuild")

    maxParallelForks = 3
  }

  check {
    dependsOn(testAlwaysSuspending)
    dependsOn(testSingleThreadSinglePartition)
  }
}
