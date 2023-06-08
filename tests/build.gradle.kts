plugins {
  java
  kotlin("jvm")
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":test-utils"))
  testImplementation(project(":contracts"))

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)

  testImplementation(libs.log4j.api)
  testRuntimeOnly(libs.log4j.core)
  testRuntimeOnly(libs.log4j.slf4j)

  testImplementation(platform(libs.jackson.bom))
  testImplementation(libs.jackson.core)
  testImplementation(libs.jackson.databind)

  testImplementation(libs.awaitility)
}

tasks {
  fun baseRestateEnvironment(name: String) =
      mapOf(
          "CONTAINER_LOGS_DIR" to "$buildDir/test-results/$name/container-logs",
          // We don't need many partitions, fewer partitions will occupy less test resources
          "RESTATE_WORKER__PARTITIONS" to "10",
          "RESTATE_RUNTIME_CONTAINER" to
              (if (System.getenv("RESTATE_RUNTIME_CONTAINER").isNullOrEmpty())
                  "ghcr.io/restatedev/restate:latest"
              else System.getenv("RESTATE_RUNTIME_CONTAINER")),
          "RUST_LOG" to (System.getenv("RUST_LOG") ?: "info,restate_invoker=trace,restate=debug"),
          "RESTATE_OBSERVABILITY__JAEGER_FILE__FILTER" to
              (System.getenv("RESTATE_OBSERVABILITY__JAEGER_FILE__FILTER")
                  ?: "info,restate_invoker=trace,restate=debug"),
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
            mapOf("RESTATE_WORKER__INVOKER__SUSPENSION_TIMEOUT" to "0s")

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
                "RESTATE_TOKIO_RUNTIME__MAX_BLOCKING_THREADS" to "1",
            )

    useJUnitPlatform {
      // Run all the tests with either no tags, or always-suspending tag
      includeTags("none() | always-suspending")
      // Increase a bit the default timeout
      systemProperties["junit.jupiter.execution.timeout.testable.method.default"] = "30 s"
    }
  }

  withType<Test>().configureEach {
    dependsOn(":services:http-server:jibDockerBuild")
    dependsOn(":services:java-services:jibDockerBuild")
    dependsOn(":services:node-services:dockerBuild")
  }
}

tasks.named("check") {
  dependsOn("testAlwaysSuspending")
  dependsOn("testSingleThreadSinglePartition")
}
