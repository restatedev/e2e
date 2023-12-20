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
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":test-utils"))
  testImplementation(project(":contracts"))

  testImplementation(libs.restate.admin)

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")

  testImplementation(libs.log4j.api)
  testRuntimeOnly(libs.log4j.core)
  testRuntimeOnly(libs.log4j.slf4j)

  testImplementation(platform(libs.jackson.bom))
  testImplementation(libs.jackson.core)
  testImplementation(libs.jackson.databind)

  testImplementation("org.apache.kafka:kafka-clients:3.5.0")

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
          // We don't need many partitions, fewer partitions will occupy less test resources
          "RESTATE_WORKER__PARTITIONS" to "10",
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

  register<Test>("testAlwaysSuspending") {
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

  register<Test>("testSingleThreadSinglePartition") {
    environment =
        environment +
            baseRestateEnvironment(name) +
            mapOf(
                "RESTATE_WORKER__PARTITIONS" to "1",
                "RESTATE_TOKIO_RUNTIME__WORKER_THREADS" to "1",
                // temporary fix for unblocking https://github.com/restatedev/restate/pull/1009,
                // should be reverted once we have
                // fixed https://github.com/restatedev/restate/issues/1013
                "RESTATE_TOKIO_RUNTIME__MAX_BLOCKING_THREADS" to "2",
            )

    useJUnitPlatform {
      // Run all the tests with either no tags, or always-suspending tag
      includeTags("none() | always-suspending")
    }
    // Increase a bit the default timeout
    systemProperties["junit.jupiter.execution.timeout.testable.method.default"] = "30 s"
  }

  register<Test>("testLazyState") {
    environment =
        environment +
            baseRestateEnvironment(name) +
            mapOf(
                "RESTATE_WORKER__INVOKER__DISABLE_EAGER_STATE" to "true",
            )

    useJUnitPlatform {
      // Run all the tests with either no tags, or always-suspending tag
      includeTags("lazy-state")
    }
  }

  register<Test>("testPersistedTimers") {
    environment =
        environment +
            baseRestateEnvironment(name) +
            mapOf("RESTATE_WORKER__TIMERS__NUM_TIMERS_IN_MEMORY_LIMIT" to "1")

    useJUnitPlatform {
      // Run all the tests with always-suspending or only-always-suspending tag
      includeTags("timers")
    }
    // Increase a bit the default timeout
    systemProperties["junit.jupiter.execution.timeout.testable.method.default"] = "20 s"
  }

  withType<Test>().configureEach {
    dependsOn(":services:http-server:jibDockerBuild")
    dependsOn(":services:java-services:jibDockerBuild")
    dependsOn(":services:node-services:dockerBuild")

    maxParallelForks = 3
  }
}

tasks.named("check") {
  dependsOn("testAlwaysSuspending")
  dependsOn("testSingleThreadSinglePartition")
  dependsOn("testPersistedTimers")
  dependsOn("testLazyState")
}
