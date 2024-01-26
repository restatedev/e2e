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

plugins {
  java
  application
  id("com.google.protobuf")
  id("com.google.cloud.tools.jib")
}

dependencies {
  implementation(libs.restate.sdk.api)
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
      artifact = "dev.restate:protoc-gen-restate:${libs.versions.restate.get()}:all@jar"
    }
  }

  generateProtoTasks { ofSourceSet("main").forEach { it.plugins { id("restate") } } }
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

tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.e2e.services.Main" } }

application { mainClass.set("dev.restate.e2e.services.Main") }
