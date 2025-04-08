import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  application
  kotlin("jvm") version "2.1.10"
  kotlin("plugin.serialization") version "2.1.10"

  alias(libs.plugins.ksp)
  id("org.jsonschema2pojo") version "1.2.2"
  alias(libs.plugins.openapi.generator)

  id("com.diffplug.spotless") version "6.25.0"
  id("com.github.jk1.dependency-license-report") version "2.9"
}

group = "dev.restate.sdktesting"

version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  // OSSRH Snapshots repo
  maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
  compileOnly(libs.tomcat.annotations)
  compileOnly(libs.google.findbugs.jsr305)

  implementation(libs.clikt)
  implementation(libs.mordant)

  ksp(libs.restate.sdk.api.kotlin.gen) { isChanging = true }
  implementation(libs.restate.sdk.kotlin.http) { isChanging = true }
  implementation(libs.vertx)

  implementation(libs.junit.all)
  implementation(libs.junit.launcher)
  implementation(libs.junit.reporting)

  implementation(libs.testcontainers.core)
  implementation(libs.testcontainers.kafka)
  implementation(libs.docker)

  implementation(libs.dotenv)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)
  implementation(libs.log4j.slf4j)
  implementation(libs.log4j.kotlin)
  implementation(libs.log4j.jul)

  implementation("org.apache.kafka:kafka-clients:3.5.0")
  implementation("org.openapitools:openapi-generator:7.12.0")

  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kaml)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.test)

  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.datetime)
  implementation(libs.jackson.toml)

  implementation(libs.assertj)
  implementation(libs.awaitility)
}

kotlin { jvmToolchain(21) }

val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")
val generatedOpenapi = layout.buildDirectory.dir("generated/openapi")

sourceSets {
  main {
    java.srcDirs(generatedJ2SPDir, layout.buildDirectory.dir("generated/openapi/src/main/java"))
  }
}

jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/json"))
  targetPackage = "dev.restate.sdktesting.infra.runtimeconfig"
  targetDirectory = generatedJ2SPDir.get().asFile

  useLongIntegers = true
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

// Configure openapi generator
openApiGenerate {
  inputSpec.set("$projectDir/src/main/openapi/admin.json")
  outputDir.set(generatedOpenapi.get().toString())

  // Java 9+ HTTP Client using Jackson
  generatorName.set("java")
  library.set("native")

  // Package names
  invokerPackage.set("dev.restate.admin.client")
  apiPackage.set("dev.restate.admin.api")
  modelPackage.set("dev.restate.admin.model")

  // We don't need these
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateModelTests.set(false)
  generateModelDocumentation.set(false)

  configOptions.put("openApiNullable", "false")
}

tasks {
  withType<KotlinCompile>().configureEach {
    dependsOn(generateJsonSchema2Pojo)
    dependsOn(withType<GenerateTask>())
  }

  test { useJUnitPlatform() }
}

afterEvaluate { tasks.named("kspKotlin").configure { mustRunAfter("openApiGenerate") } }

spotless {
  kotlin {
    ktfmt()
    targetExclude("build/generated/**/*.kt")
    licenseHeaderFile("$rootDir/config/license-header")
  }
  kotlinGradle { ktfmt() }
}

licenseReport {
  renderers =
      arrayOf<com.github.jk1.license.render.ReportRenderer>(
          com.github.jk1.license.render.CsvReportRenderer())

  excludeBoms = true

  excludes =
      arrayOf(
          "io.vertx:vertx-stack-depchain", // Vertx bom file
          "com.google.guava:guava-parent", // Guava bom
          // kotlinx dependencies are APL 2, but somehow the plugin doesn't recognize that.
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

application { mainClass = "dev.restate.sdktesting.MainKt" }
