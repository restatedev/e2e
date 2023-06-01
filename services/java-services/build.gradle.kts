import dev.restate.e2e.gradle.util.hostArchitecture
import dev.restate.e2e.gradle.util.testBaseImage

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(libs.restate.sdk.core)
  implementation(libs.restate.sdk.blocking)
  implementation(libs.restate.sdk.vertx)
  implementation(libs.restate.sdk.jackson)

  implementation(project(":contracts"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.grpc.netty.shaded)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)

  implementation(platform(libs.vertx.bom))
  implementation(libs.vertx.core)
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
