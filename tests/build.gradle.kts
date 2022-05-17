plugins {
    java
    kotlin("jvm") version "1.6.20"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":functions:counter:contract"))

    testImplementation(libs.junit5)
    testImplementation(libs.assertj)

    testRuntimeOnly(libs.log4j.api)
    testRuntimeOnly(libs.log4j.core)
    testRuntimeOnly(libs.log4j.slf4j)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
}
