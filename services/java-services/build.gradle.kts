// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf
import dev.restate.e2e.gradle.util.hostArchitecture
import dev.restate.e2e.gradle.util.testBaseImage

plugins {
  java
  application
  alias(libs.plugins.protobuf)
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(libs.restate.sdk.java.blocking)
  implementation(libs.restate.sdk.http.vertx)
  implementation(libs.restate.sdk.jackson)

  implementation(project(":contracts"))
  protobuf(project(":contracts"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.grpc.netty.shaded)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }

  plugins {
    id("restate") {
      artifact =
          "dev.restate.sdk:protoc-gen-restate-java-blocking:${libs.versions.restate.get()}:all@jar"
    }
  }

  generateProtoTasks { ofSourceSet("main").forEach { it.plugins { id("restate") } } }
}

jib {
  to.image = "restatedev/e2e-java-services"
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
tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.e2e.services.Main" } }

application { mainClass.set("dev.restate.e2e.services.Main") }
