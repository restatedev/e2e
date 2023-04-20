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

  implementation(project(":contracts"))
  implementation(project(":functions:utils"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.grpc.netty.shaded)

  implementation(platform(libs.vertx.bom))
  implementation(libs.vertx.core)
}

jib {
  to.image = "restatedev/e2e-collections"
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
tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.e2e.functions.collections.Main" } }
