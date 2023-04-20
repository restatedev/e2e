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

tasks.withType<Test> {
  dependsOn(":functions:collections:jibDockerBuild")
  dependsOn(":functions:counter:jibDockerBuild")
  dependsOn(":functions:coordinator:jibDockerBuild")
  dependsOn(":functions:externalcall:jibDockerBuild")
  dependsOn(":functions:errors:jibDockerBuild")
  dependsOn(":functions:http-server:jibDockerBuild")

  environment =
      environment +
          mapOf(
              "CONTAINER_LOGS_DIR" to "$buildDir/test-results/$name/container-logs",
              "RESTATE_RUNTIME_CONTAINER" to "ghcr.io/restatedev/restate:latest")
}
