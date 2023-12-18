// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("org.jsonschema2pojo") version "1.2.1"
}

dependencies {
  api(libs.junit.api)
  api(libs.testcontainers.core)
  api(libs.testcontainers.kafka)

  api(libs.grpc.stub)

  // We need this to compile the code generated, can't remove these annotations from code gen :(
  compileOnly("jakarta.validation:jakarta.validation-api:3.0.2")

  implementation(libs.restate.admin)
  implementation(libs.log4j.api)
  implementation(libs.grpc.netty.shaded)
  implementation(libs.grpc.protobuf)

  implementation(libs.testcontainers.toxiproxy)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.yaml)

  testImplementation(libs.junit.all)
  testImplementation(libs.assertj)
}

val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")

sourceSets { main { java.srcDir(generatedJ2SPDir) } }

jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/json"))
  targetPackage = "dev.restate.e2e.utils.config"
  targetDirectory = generatedJ2SPDir.get().asFile

  useLongIntegers = true
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { dependsOn(generateJsonSchema2Pojo) }
  withType<JavaCompile> { dependsOn(generateJsonSchema2Pojo) }
  withType<Jar> { dependsOn(generateJsonSchema2Pojo) }
}
