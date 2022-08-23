plugins {
  java
  kotlin("jvm") version "1.6.20"
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
  testImplementation(libs.jackson.yaml)

  testImplementation(platform(libs.cloudevents.bom))
  testImplementation(libs.cloudevents.core)
  testImplementation(libs.cloudevents.kafka)
  testImplementation(libs.cloudevents.json)

  testImplementation(platform(libs.testcontainers.bom))
  testImplementation(libs.testcontainers.core)
  testImplementation(libs.testcontainers.kafka)
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
              "CONTAINER_LOGS_DIR" to "$buildDir/test-results/container-logs",
              "RESTATE_RUNTIME_CONTAINER" to "ghcr.io/restatedev/runtime:main",
              "DESCRIPTORS_FILE" to
                  "${rootProject.projectDir}/.restate/descriptors/contracts.descriptor")
}
