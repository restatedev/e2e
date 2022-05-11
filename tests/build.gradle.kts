import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    kotlin("jvm") version "1.6.20"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
