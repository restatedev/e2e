plugins {
    java
    kotlin("jvm") version "1.6.20"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":test-utils"))
    testImplementation(project(":functions:counter:contract"))

    testImplementation(libs.protobuf.java)
    testImplementation(libs.grpc.stub)
    testImplementation(libs.grpc.protobuf)

    testImplementation(libs.junit5)
    testImplementation(libs.assertj)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
}
