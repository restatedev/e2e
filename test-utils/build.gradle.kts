plugins {
  java
  kotlin("jvm") version "1.6.20"
  `maven-publish`
}

dependencies {
  implementation(libs.slf4j)
  implementation(libs.junit5)
  implementation(platform(libs.testcontainers.bom))
  implementation(libs.testcontainers.core)
  implementation(libs.testcontainers.kafka)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.yaml)

  implementation(libs.grpc.stub)
  implementation(libs.grpc.netty.shaded)
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
