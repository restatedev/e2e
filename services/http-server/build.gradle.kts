// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import dev.restate.e2e.gradle.util.hostArchitecture
import dev.restate.e2e.gradle.util.testBaseImage

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(project(":contracts"))
  implementation(libs.grpc.netty.shaded)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  errorprone(libs.errorprone)
}

jib {
  to.image = "restatedev/e2e-http-server"
  from.image = testBaseImage()

  from {
    platforms {
      platform {
        architecture = hostArchitecture()
        os = "linux"
      }
    }
  }
}

// Use gradle shadowJar to build the fat jar
tasks.jar {
  manifest { attributes["Main-Class"] = "dev.restate.e2e.testing.externalhttpserver.Main" }
}
