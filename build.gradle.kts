// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentArchitecture

plugins {
  java

  // Declare plugins used by subprojects
  id("com.diffplug.spotless") version "6.22.0" apply false
  kotlin("jvm") version "1.9.22" apply false
  kotlin("plugin.serialization") version "1.9.22" apply false
  id("com.google.cloud.tools.jib") version "3.2.1" apply false
}

val restateVersion = libs.versions.restate.get()

// Configuration of jib container images parameters

fun testHostArchitecture(): String {
  val currentArchitecture = getCurrentArchitecture()

  return if (currentArchitecture.isAmd64) {
    "amd64"
  } else {
    when (currentArchitecture.name) {
      "arm-v8",
      "aarch64",
      "arm64",
      "aarch_64" -> "arm64"
      else ->
          throw IllegalArgumentException("Not supported host architecture: $currentArchitecture")
    }
  }
}

fun testBaseImage(): String {
  return when (testHostArchitecture()) {
    "arm64" ->
        "eclipse-temurin:17-jre@sha256:61c5fee7a5c40a1ca93231a11b8caf47775f33e3438c56bf3a1ea58b7df1ee1b"
    "amd64" ->
        "eclipse-temurin:17-jre@sha256:ff7a89fe868ba504b09f93e3080ad30a75bd3d4e4e7b3e037e91705f8c6994b3"
    else ->
        throw IllegalArgumentException("No image for host architecture: ${testHostArchitecture()}")
  }
}

ext {
  set("testHostArchitecture", testHostArchitecture())
  set("testBaseImage", testBaseImage())
}

allprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "com.diffplug.spotless")

  version = restateVersion

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
      ktfmt()
      targetExclude("build/generated/**/*.kt")
    }
    kotlinGradle { ktfmt() }
    java {
      googleJavaFormat()
      targetExclude("build/generated/**/*.java")
    }
  }

  // Configure the java toolchain to use. If not found, it will be downloaded automatically
  java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
}

buildscript {
  // required for m1 mac
  configurations { classpath { resolutionStrategy { force("net.java.dev.jna:jna:5.7.0") } } }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "com.diffplug.spotless")

  tasks.withType<Test> { useJUnitPlatform() }

  configurations.all {
    // This disables caching for -SNAPSHOT dependencies
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
  }
}
