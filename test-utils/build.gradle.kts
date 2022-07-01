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

java {
  withJavadocJar()
  withSourcesJar()
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

    maven {
      name = "JFrog"
      val releasesRepoUrl = uri("https://restatedev.jfrog.io/artifactory/restatedev-libs-release")
      val snapshotsRepoUrl = uri("https://restatedev.jfrog.io/artifactory/restatedev-libs-snapshot")
      url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

      credentials {
        username = System.getenv("JFROG_USERNAME")
        password = System.getenv("JFROG_TOKEN")
      }
    }
  }

  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.testing"
      artifactId = "e2e-utils"

      from(components["java"])
    }
  }
}
