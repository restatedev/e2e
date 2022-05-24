import dev.restate.e2e.gradle.util.hostArchitecture

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(libs.restate.sdk)
  implementation(project(":functions:counter:contract"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.grpc.netty.shaded)
}

jib {
  to.image = "restatedev/e2e-counter"

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
tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.e2e.functions.counter.Main" } }
