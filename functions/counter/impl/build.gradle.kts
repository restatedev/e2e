plugins {
    java
    idea
    alias(libs.plugins.shadowJar)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.jib)
}

dependencies {
    implementation(libs.restate.sdk)
    implementation(project(":functions:counter:contract"))

    implementation(libs.protobuf.java)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf)

    implementation(libs.log4j.api)
    implementation(libs.log4j.core)

    implementation(libs.grpc.netty.shaded)

    errorprone(libs.errorprone)
}

jib {
    to.image = "restatedev/e2e-counter"
}

// Use gradle shadowJar to build the fat jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.restate.e2e.functions.counter.Main"
    }
}