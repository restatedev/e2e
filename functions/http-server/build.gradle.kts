import dev.restate.e2e.gradle.util.hostArchitecture

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.jib)
}

dependencies {
  implementation(project(":functions:externalcall:contract"))

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
