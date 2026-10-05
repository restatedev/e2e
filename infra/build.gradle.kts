import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")

  id("org.jsonschema2pojo")
  alias(libs.plugins.openapi.generator)
  alias(libs.plugins.protobuf)

  id("com.diffplug.spotless")
  id("com.github.jk1.dependency-license-report")
}

kotlin { jvmToolchain(25) }

val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")
val generatedOpenapi = layout.buildDirectory.dir("generated/openapi")
val generatedProto = layout.buildDirectory.dir("generated/sources/proto/main/java")
val generatedGrpc = layout.buildDirectory.dir("generated/sources/proto/main/grpc")

sourceSets {
  main {
    java.srcDirs(
        generatedJ2SPDir,
        layout.buildDirectory.dir("generated/openapi/src/main/java"),
        generatedProto,
        generatedGrpc,
    )
  }
}

dependencies {
  compileOnly(libs.tomcat.annotations)
  compileOnly(libs.google.findbugs.jsr305)

  implementation(libs.clikt)
  implementation(libs.mordant)
  implementation(libs.dotenv)
  implementation(libs.restate.sdk.client.kotlin)
  implementation(libs.restate.sdk.kotlin.http)
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

  implementation(libs.grpc.netty.shaded)
  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.stub)
  implementation(libs.protobuf.java)
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
  plugins {
    create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" }
  }
  generateProtoTasks { all().configureEach { plugins { create("grpc") } } }
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

openApiGenerate {
  inputSpec.set("$projectDir/src/main/openapi/admin.json")
  outputDir.set(generatedOpenapi.get().toString())

  generatorName.set("java")
  library.set("native")

  invokerPackage.set("dev.restate.admin.client")
  apiPackage.set("dev.restate.admin.api")
  modelPackage.set("dev.restate.admin.model")

  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateModelTests.set(false)
  generateModelDocumentation.set(false)

  configOptions.put("openApiNullable", "false")
}

tasks {
  withType<KotlinCompile>().configureEach {
    dependsOn("generateProto")
    dependsOn(openApiGenerate)
    dependsOn(generateJsonSchema2Pojo)
    dependsOn(withType<GenerateTask>())
  }
}

spotless {
  kotlin {
    ktfmt()
    targetExclude("build/generated/**/*.kt")
    licenseHeaderFile("$rootDir/config/license-header")
  }
  kotlinGradle { ktfmt() }
  java {
    googleJavaFormat()
    targetExclude("build/generated/**/*.java")
    licenseHeaderFile("$rootDir/config/license-header")
  }
}

tasks.named("check") { dependsOn("checkLicense") }

licenseReport {
  renderers =
      arrayOf<com.github.jk1.license.render.ReportRenderer>(
          com.github.jk1.license.render.CsvReportRenderer()
      )

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
              "$rootDir/config/license-normalizer-bundle.json",
              true,
          )
      )
}
