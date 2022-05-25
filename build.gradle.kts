import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20"
    java
    alias(libs.plugins.spotless)
}

val testReport = tasks.register<TestReport>("testReport") {
    destinationDirectory.set(file("$buildDir/reports/tests/test"))
    testResults.setFrom(subprojects.mapNotNull {
        it.tasks.findByPath("test")
    })
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
                TestLogEvent.STANDARD_OUT
            )
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        targetCompatibility = "11"
        sourceCompatibility = "11"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            ktfmt()
        }
        kotlinGradle {
            ktfmt()
        }
        java {
            googleJavaFormat()
        }
    }
}
