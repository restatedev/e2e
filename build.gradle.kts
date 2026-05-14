plugins {
  kotlin("jvm") version "2.1.10" apply false
  kotlin("plugin.serialization") version "2.1.10" apply false
  kotlin("plugin.allopen") version "2.1.10" apply false

  id("org.jsonschema2pojo") version "1.2.2" apply false
  alias(libs.plugins.openapi.generator) apply false

  id("com.diffplug.spotless") version "6.25.0" apply false
  id("com.github.jk1.dependency-license-report") version "2.9" apply false
  alias(libs.plugins.shadow) apply false
}

subprojects {
  group = "dev.restate.sdktesting"
  version = "1.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}
