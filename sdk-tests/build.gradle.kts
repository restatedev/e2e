plugins {
  application
  kotlin("jvm")
  kotlin("plugin.serialization")
  kotlin("plugin.allopen")

  alias(libs.plugins.shadow)

  id("com.diffplug.spotless")
  id("com.github.jk1.dependency-license-report")
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":infra"))

  compileOnly(libs.tomcat.annotations)
  compileOnly(libs.google.findbugs.jsr305)

  implementation(libs.clikt)
  implementation(libs.mordant)

  implementation(libs.restate.sdk.client.kotlin)
  implementation(libs.restate.sdk.kotlin.http)
  implementation(libs.vertx)
  implementation(libs.dotenv)

  implementation(libs.junit.all)
  implementation(libs.junit.launcher)
  implementation(libs.junit.reporting)

  implementation(libs.testcontainers.core)
  implementation(libs.docker)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)
  implementation(libs.log4j.slf4j)
  implementation(libs.log4j.kotlin)
  implementation(libs.log4j.jul)

  implementation(libs.assertj)
  implementation(libs.awaitility)

  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kaml)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.test)
}

allOpen {
  annotation("dev.restate.sdk.annotation.Service")
  annotation("dev.restate.sdk.annotation.VirtualObject")
  annotation("dev.restate.sdk.annotation.Workflow")
}

application { mainClass = "dev.restate.sdktesting.MainKt" }

tasks.shadowJar { archiveClassifier = "" }

spotless {
  kotlin {
    ktfmt()
    licenseHeaderFile("$rootDir/config/license-header")
  }
  kotlinGradle { ktfmt() }
}

tasks.named("check") { dependsOn("checkLicense") }

licenseReport {
  renderers =
      arrayOf<com.github.jk1.license.render.ReportRenderer>(
          com.github.jk1.license.render.CsvReportRenderer())

  excludeBoms = true

  excludes =
      arrayOf(
          "io.vertx:vertx-stack-depchain",
          "com.google.guava:guava-parent",
          "org.jetbrains.kotlinx:kotlinx-coroutines-core",
          "org.jetbrains.kotlinx:kotlinx-serialization-core",
          "org.jetbrains.kotlinx:kotlinx-serialization-json",
      )

  allowedLicensesFile = file("$rootDir/config/allowed-licenses.json")
  filters =
      arrayOf(
          com.github.jk1.license.filter.LicenseBundleNormalizer(
              "$rootDir/config/license-normalizer-bundle.json", true))
}
