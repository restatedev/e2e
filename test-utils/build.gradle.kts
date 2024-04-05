// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.github.jk1.dependency-license-report") version "2.1"
  id("org.jsonschema2pojo") version "1.2.1"
}

dependencies {
  api(libs.junit.api)
  api(libs.testcontainers.core)
  api(libs.testcontainers.kafka)
  api(libs.restate.sdk.common)

  implementation(libs.restate.admin)
  implementation(libs.log4j.api)

  implementation(libs.testcontainers.toxiproxy)

  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.toml)

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)
}

val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")

sourceSets { main { java.srcDir(generatedJ2SPDir) } }

jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/json"))
  targetPackage = "dev.restate.e2e.utils.config"
  targetDirectory = generatedJ2SPDir.get().asFile

  useLongIntegers = true
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { dependsOn(generateJsonSchema2Pojo) }
  withType<JavaCompile> { dependsOn(generateJsonSchema2Pojo) }
  withType<Jar> { dependsOn(generateJsonSchema2Pojo) }

  check { dependsOn(checkLicense) }
}

licenseReport {
  renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())

  excludeBoms = true

  excludes =
      arrayOf(
          "dev.restate:.*", // Our own dependencies imported through JAVA_SDK_LOCAL_BUILD won't have
          // a license
      )

  allowedLicensesFile = file("$rootDir/config/allowed-licenses.json")
  filters =
      arrayOf(
          com.github.jk1.license.filter.LicenseBundleNormalizer(
              "$rootDir/config/license-normalizer-bundle.json", true))
}
