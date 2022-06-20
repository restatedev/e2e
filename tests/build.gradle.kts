plugins {
  java
  kotlin("jvm") version "1.6.20"
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(project(":test-utils"))
  testImplementation(project(":functions:counter:contract"))
  testImplementation(project(":functions:coordinator:contract"))
  testImplementation(project(":functions:externalcall:contract"))

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
  dependsOn(":functions:counter:impl:jibDockerBuild")
  dependsOn(":functions:coordinator:impl:jibDockerBuild")
  dependsOn(":functions:externalcall:impl:jibDockerBuild")
  dependsOn(":functions:http-server:jibDockerBuild")
}
