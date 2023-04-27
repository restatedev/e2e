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

  withType<Test>().configureEach {
    dependsOn(":services:http-server:jibDockerBuild")
    dependsOn(":services:java-services:jibDockerBuild")
    dependsOn(":services:node-services:docker")

    environment =
        environment +
            mapOf(
                "CONTAINER_LOGS_DIR" to "$buildDir/test-results/$name/container-logs",
                "RESTATE_RUNTIME_CONTAINER" to "ghcr.io/restatedev/restate:latest",
                "RUST_LOG" to "info,hyper=trace,restate_invoker=trace,restate=debug",
                "RUST_BACKTRACE" to "full")
  }
}

tasks.named("build") { dependsOn("test-always-suspending") }
