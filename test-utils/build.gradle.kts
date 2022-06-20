plugins {
  java
  kotlin("jvm") version "1.6.20"
  `maven-publish`
}

dependencies {
  api(libs.junit.api)
  api(platform(libs.testcontainers.bom))
  api(libs.testcontainers.core)
  api(libs.testcontainers.kafka)

  api(libs.grpc.stub)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.yaml)

  implementation(libs.log4j.api)
  implementation(libs.grpc.netty.shaded)

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)
}

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/restatedev/e2e")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
  publications {
    register<MavenPublication>("gpr") {
      groupId = "dev.restate.testing"
      artifactId = "e2e-utils"
      version = "1.0-SNAPSHOT"

      from(components["java"])
    }
  }
}
