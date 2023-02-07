import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.20"
  java
  alias(libs.plugins.spotless)
  id("com.github.jk1.dependency-license-report") version "2.0"
}

val restateVersion = libs.versions.restate.get()

val testReport =
    tasks.register<TestReport>("testReport") {
      destinationDirectory.set(file("$buildDir/reports/tests/test"))
      testResults.setFrom(subprojects.mapNotNull { it.tasks.findByPath("test") })
    }

allprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "com.github.jk1.dependency-license-report")

  version = restateVersion

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { ktfmt() }
    kotlinGradle { ktfmt() }
    java { googleJavaFormat() }
  }

  tasks { check { dependsOn(checkLicense) } }

  licenseReport {
    renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())

    excludeBoms = true

    excludes =
        arrayOf(
            "io.vertx:vertx-stack-depchain", // Vertx bom file
            "org.jetbrains.kotlinx:kotlinx-coroutines-core", // Kotlinx coroutines bom file
            "dev.restate.sdk:java-sdk", // Our own dependency has no license yet
            "java-sdk:sdk", // Exclude java-sdk from composite build (dev.restate.sdk:java-sdk gets
            // replace by java-sdk:sdk)
            )

    allowedLicensesFile = file("$rootDir/config/allowed-licenses.json")
    filters =
        arrayOf(
            com.github.jk1.license.filter.LicenseBundleNormalizer(
                "$rootDir/config/license-normalizer-bundle.json", true))
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "com.diffplug.spotless")

  tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(testReport)
    testLogging {
      events(
          TestLogEvent.PASSED,
          TestLogEvent.SKIPPED,
          TestLogEvent.FAILED,
          TestLogEvent.STANDARD_ERROR,
          TestLogEvent.STANDARD_OUT)
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = JavaVersion.VERSION_11.toString()
    sourceCompatibility = JavaVersion.VERSION_11.toString()
  }

  tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString() }

  configurations.all {
    // This disables caching for -SNAPSHOT dependencies
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
  }
}
