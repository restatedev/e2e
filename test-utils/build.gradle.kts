plugins {
  java
  kotlin("jvm") version "1.8.10"
  kotlin("plugin.serialization") version "1.8.10"
  `maven-publish`
  id("com.github.jk1.dependency-license-report") version "2.1"
}

dependencies {
  api(libs.junit.api)
  api(libs.testcontainers.core)
  api(libs.testcontainers.kafka)

  api(libs.grpc.stub)

  implementation(libs.log4j.api)
  implementation(libs.grpc.netty.shaded)
  implementation(libs.grpc.protobuf)

  implementation(libs.testcontainers.toxiproxy)

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")

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

tasks { check { dependsOn(checkLicense) } }

licenseReport {
  renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())

  excludeBoms = true

  excludes =
      arrayOf(
          "dev.restate.sdk:.*", // Our own dependency has no license yet
      )

  allowedLicensesFile = file("$rootDir/config/allowed-licenses.json")
  filters =
      arrayOf(
          com.github.jk1.license.filter.LicenseBundleNormalizer(
              "$rootDir/config/license-normalizer-bundle.json", true))
}
