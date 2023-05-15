plugins {
  java
  kotlin("jvm") version "1.8.10"
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
  test {
    useJUnitPlatform {
      // Run all the tests with either no tags, or always-suspending tag
      includeTags("none() | always-suspending")
    }
  }

  register<Test>("test-always-suspending") {
    environment = environment + mapOf("RESTATE_WORKER__INVOKER__SUSPENSION_TIMEOUT" to "0s")

    useJUnitPlatform {
      // Run all the tests with always-suspending tag
      includeTags("always-suspending")
    }
  }

  register<Test>("test-single-thread-single-partition") {
    environment =
        environment +
            mapOf(
                "RESTATE_WORKER__PARTITIONS" to "1",
                "RESTATE_TOKIO_RUNTIME__WORKER_THREADS" to "1",
                "RESTATE_TOKIO_RUNTIME__MAX_BLOCKING_THREADS" to "1",
            )

    useJUnitPlatform {
      // Run all the tests with either no tags, or always-suspending tag
      includeTags("none() | always-suspending")
    }
  }

  withType<Test>().configureEach {
    dependsOn(":services:http-server:jibDockerBuild")
    dependsOn(":services:java-services:jibDockerBuild")
    dependsOn(":services:node-services:dockerBuild")

    environment =
        environment +
            mapOf(
                "CONTAINER_LOGS_DIR" to "$buildDir/test-results/$name/container-logs",
                "RESTATE_RUNTIME_CONTAINER" to
                    (System.getenv("RESTATE_RUNTIME_CONTAINER")
                        ?: "ghcr.io/restatedev/restate:latest"),
                "RUST_LOG" to
                    (System.getenv("RUST_LOG") ?: "info,restate_invoker=trace,restate=debug"),
                "RESTATE_OBSERVABILITY__JAEGER_FILE__FILTER" to
                    (System.getenv("RESTATE_OBSERVABILITY__JAEGER_FILE__FILTER")
                        ?: "info,restate_invoker=trace,restate=debug"),
                "RUST_BACKTRACE" to "full")
  }
}

tasks.named("build") {
  dependsOn("test-always-suspending")
  dependsOn("test-single-thread-single-partition")
}
