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
  application
  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(libs.restate.sdk.api.kotlin)
  implementation(libs.restate.sdk.http.vertx)

  implementation(project(":contracts-kt"))
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)
}

jib {
  to.image = "restatedev/e2e-kotlin-services"
  from.image = parent!!.parent!!.ext.get("testBaseImage").toString()

  from {
    platforms {
      platform {
        architecture = parent!!.parent!!.ext.get("testHostArchitecture").toString()
        os = "linux"
      }
    }
  }
}

tasks.jar { manifest { attributes["Main-Class"] = "my.restate.e2e.services.kotlin.AppKt" } }

application { mainClass.set("my.restate.e2e.services.kotlin.AppKt") }
