import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  kotlin("jvm") version "1.8.10"
  java
  alias(libs.plugins.spotless)
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

  version = restateVersion

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin { ktfmt() }
    kotlinGradle { ktfmt() }
    java {
      googleJavaFormat()
      targetExclude("build/generated/**/*.java")
    }
  }

  java.targetCompatibility = JavaVersion.VERSION_11
  java.sourceCompatibility = JavaVersion.VERSION_11

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
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

  configurations.all {
    // This disables caching for -SNAPSHOT dependencies
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
  }
}
