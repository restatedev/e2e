import dev.restate.e2e.gradle.util.hostArchitecture
import dev.restate.e2e.gradle.util.testBaseImage

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(libs.restate.sdk)

  implementation(project(":functions:utils"))
  implementation(project(":functions:collections:contract"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.grpc.netty.shaded)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
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
