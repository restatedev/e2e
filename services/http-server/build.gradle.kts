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
  id("net.ltgt.errorprone")
  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(project(":contracts"))

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.restate.sdk.api)

  errorprone(libs.errorprone)
}

jib {
  to.image = "restatedev/e2e-http-server"
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

tasks.jar {
  manifest { attributes["Main-Class"] = "dev.restate.e2e.testing.externalhttpserver.Main" }
}
