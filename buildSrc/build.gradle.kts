// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
  targetCompatibility = JavaVersion.VERSION_11.toString()
  sourceCompatibility = JavaVersion.VERSION_11.toString()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}
