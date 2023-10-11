import dev.restate.e2e.gradle.util.hostArchitecture
import dev.restate.e2e.gradle.util.testBaseImage

plugins {
  java
  idea
  alias(libs.plugins.shadowJar)
  alias(libs.plugins.jib)
  application
}

dependencies {
  implementation(libs.restate.sdk.java.blocking)
  implementation(libs.restate.sdk.http.vertx)
  implementation(libs.restate.sdk.jackson)

  implementation(project(":contracts"))

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(libs.grpc.netty.shaded)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
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
