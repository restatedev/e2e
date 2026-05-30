// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
plugins {
  application
  kotlin("jvm")
  kotlin("plugin.allopen")

  alias(libs.plugins.shadow)

  id("com.diffplug.spotless")
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":invoker-memory-kotlin-contracts"))

  implementation(libs.restate.sdk.kotlin.http)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)
  implementation(libs.log4j.slf4j)

  implementation(libs.kotlinx.coroutines.core)
}

allOpen {
  annotation("dev.restate.sdk.annotation.Service")
  annotation("dev.restate.sdk.annotation.VirtualObject")
  annotation("dev.restate.sdk.annotation.Workflow")
}

application { mainClass = "dev.restate.sdktesting.invokermemory.MainKt" }

// Produces build/libs/invoker-memory-kotlin-service.jar so the Dockerfile can COPY it
// with a stable, classifier-less name.
tasks.shadowJar {
  archiveBaseName = "invoker-memory-kotlin-service"
  archiveClassifier = ""
  archiveVersion = ""
}

spotless {
  kotlin {
    ktfmt()
    licenseHeaderFile("$rootDir/config/license-header")
  }
  kotlinGradle { ktfmt() }
}
