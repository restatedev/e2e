// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  alias(libs.plugins.ksp)
}

dependencies {
  ksp(libs.restate.sdk.api.kotlin.gen)
  api(libs.restate.sdk.api.kotlin)
  implementation(libs.kotlinx.serialization.core)
}
