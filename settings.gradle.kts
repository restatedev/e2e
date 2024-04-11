// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

rootProject.name = "restate-e2e"

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0" }

include(
    "contracts",
    "contracts-kt",
    "services:java-services",
    "services:kotlin-services",
    "services:node-services",
    "services:http-server",
    "test-utils",
    "tests")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    // OSSRH Snapshots repo
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
  }

  versionCatalogs {
    create("libs") {
      library("restate-sdk-common", "dev.restate", "sdk-common").versionRef("restate")
      library("restate-admin", "dev.restate", "admin-client").versionRef("restate")
      library("restate-sdk-api", "dev.restate", "sdk-api").versionRef("restate")
      library("restate-sdk-api-gen", "dev.restate", "sdk-api-gen").versionRef("restate")
      library("restate-sdk-api-kotlin", "dev.restate", "sdk-api-kotlin").versionRef("restate")
      library("restate-sdk-api-kotlin-gen", "dev.restate", "sdk-api-kotlin-gen")
          .versionRef("restate")
      library("restate-sdk-jackson", "dev.restate", "sdk-serde-jackson").versionRef("restate")
      library("restate-sdk-http-vertx", "dev.restate", "sdk-http-vertx").versionRef("restate")
      library("restate-sdk-workflow-api", "dev.restate", "sdk-workflow-api").versionRef("restate")

      version("log4j", "2.19.0")
      library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j")
      library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j")
      library("log4j-slf4j", "org.apache.logging.log4j", "log4j-slf4j-impl").versionRef("log4j")

      version("jackson", "2.15.2")
      library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").versionRef("jackson")
      library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind")
          .versionRef("jackson")
      library("jackson-kotlin", "com.fasterxml.jackson.module", "jackson-module-kotlin")
          .versionRef("jackson")
      library("jackson-toml", "com.fasterxml.jackson.dataformat", "jackson-dataformat-toml")
          .versionRef("jackson")

      version("junit-jupiter", "5.10.0")
      library("junit-all", "org.junit.jupiter", "junit-jupiter").versionRef("junit-jupiter")
      library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit-jupiter")

      version("assertj", "3.24.2")
      library("assertj", "org.assertj", "assertj-core").versionRef("assertj")

      version("testcontainers", "1.19.0")
      library("testcontainers-core", "org.testcontainers", "testcontainers")
          .versionRef("testcontainers")
      library("testcontainers-kafka", "org.testcontainers", "kafka").versionRef("testcontainers")
      library("testcontainers-toxiproxy", "org.testcontainers", "toxiproxy")
          .versionRef("testcontainers")

      version("awaitility", "4.2.1")
      library("awaitility", "org.awaitility", "awaitility-kotlin").versionRef("awaitility")

      library("kotlinx-serialization-core", "org.jetbrains.kotlinx", "kotlinx-serialization-core")
          .version("1.6.2")
      library("kotlinx-serialization-json", "org.jetbrains.kotlinx", "kotlinx-serialization-json")
          .version("1.6.2")

      version("ksp", "1.9.22-1.0.18")
      library("symbol-processing-api", "com.google.devtools.ksp", "symbol-processing-api")
          .versionRef("ksp")
      plugin("ksp", "com.google.devtools.ksp").versionRef("ksp")
    }
  }
}

// Include composite build for easier local testing
if (!System.getenv("JAVA_SDK_LOCAL_BUILD").isNullOrEmpty()) {
  includeBuild("../sdk-java") {
    dependencySubstitution {
      substitute(module("dev.restate:admin-client")).using(project(":admin-client"))
      substitute(module("dev.restate:sdk-common")).using(project(":sdk-common"))
      substitute(module("dev.restate:sdk-api")).using(project(":sdk-api"))
      substitute(module("dev.restate:sdk-api-gen")).using(project(":sdk-api-gen"))
      substitute(module("dev.restate:sdk-api-kotlin")).using(project(":sdk-api-kotlin"))
      substitute(module("dev.restate:sdk-api-kotlin-gen")).using(project(":sdk-api-kotlin-gen"))
      substitute(module("dev.restate:sdk-workflow-api")).using(project(":sdk-workflow-api"))
      substitute(module("dev.restate:sdk-http-vertx")).using(project(":sdk-http-vertx"))
      substitute(module("dev.restate:sdk-serde-jackson")).using(project(":sdk-serde-jackson"))
    }
  }
}
