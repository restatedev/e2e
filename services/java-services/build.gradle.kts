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
  application
  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(libs.restate.sdk.api)
  implementation(libs.restate.sdk.http.vertx)
  implementation(libs.restate.sdk.jackson)

  implementation(project(":contracts"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
}

jib {
  to.image = "restatedev/e2e-java-services"
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

tasks.jar { manifest { attributes["Main-Class"] = "my.restate.e2e.services.Main" } }

application { mainClass.set("my.restate.e2e.services.Main") }
