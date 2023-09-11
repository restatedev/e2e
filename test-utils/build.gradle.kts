plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
  `maven-publish`
  id("com.github.jk1.dependency-license-report") version "2.1"
  id("org.jsonschema2pojo") version "1.2.1"
}

// Dependency set for code-generating the openapi client
val fabrikt: Configuration by configurations.creating

dependencies {
  fabrikt("com.cjbooms:fabrikt:9.0.1")

  api(libs.junit.api)
  api(libs.testcontainers.core)
  api(libs.testcontainers.kafka)

  api(libs.grpc.stub)

  // Dependencies for the Meta client
  api("com.squareup.okhttp3:okhttp:4.10.0")
  api(platform(libs.jackson.bom))
  api(libs.jackson.core)
  api(libs.jackson.databind)
  api(libs.jackson.kotlin)

  // We need this to compile the code generated, can't remove these annotations from code gen :(
  compileOnly("jakarta.validation:jakarta.validation-api:3.0.2")

  implementation(libs.log4j.api)
  implementation(libs.grpc.netty.shaded)
  implementation(libs.grpc.protobuf)

  implementation(libs.testcontainers.toxiproxy)

  implementation(libs.jackson.yaml)

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)
}

val apiFile = "$projectDir/src/main/openapi/meta.json"
val generatedDir = "$buildDir/generated"

sourceSets {
  main {
    java.srcDir("$generatedDir/j2sp")
    kotlin.srcDir("$generatedDir/src/main/kotlin")
  }
}

java {
  withJavadocJar()
  withSourcesJar()
}

jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/json"))
  targetPackage = "dev.restate.e2e.utils.config"
  targetDirectory = file("$generatedDir/j2sp")

  useLongIntegers = true
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

tasks {
  val generateCode by
      creating(JavaExec::class) {
        inputs.files(apiFile)
        outputs.dir(generatedDir)
        outputs.cacheIf { true }
        classpath(fabrikt)
        mainClass.set("com.cjbooms.fabrikt.cli.CodeGen")
        args =
            listOf(
                "--output-directory",
                generatedDir,
                "--base-package",
                "dev.restate.e2e.utils.meta",
                "--api-file",
                apiFile,
                "--validation-library",
                "JAKARTA_VALIDATION",
                "--targets",
                "http_models",
                "--targets",
                "client",
            )
      }

  // Make sure generateCode is correctly linked to compilation tasks
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateCode, generateJsonSchema2Pojo)
  }
  withType<JavaCompile> { dependsOn(generateCode, generateJsonSchema2Pojo) }
  withType<Jar> { dependsOn(generateCode, generateJsonSchema2Pojo) }

  check { dependsOn(checkLicense) }
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
    register<MavenPublication>("maven") {
      groupId = "dev.restate.testing"
      artifactId = "e2e-utils"

      from(components["java"])
    }
  }
}

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
