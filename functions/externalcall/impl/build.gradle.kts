import dev.restate.e2e.gradle.util.hostArchitecture
import dev.restate.e2e.gradle.util.testBaseImage

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.spotless)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(libs.restate.sdk)

  implementation(project(":functions:utils"))
  implementation(project(":functions:externalcall:contract"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)

  implementation(libs.grpc.netty.shaded)

  errorprone(libs.errorprone)
}

jib {
  to.image = "restatedev/e2e-externalcall"
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
tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.e2e.functions.externalcall.Main" } }
