plugins {
  `java-library`
  idea
  alias(libs.plugins.spotless)
  alias(libs.plugins.errorprone)
}

dependencies {
  implementation(libs.restate.sdk)
  implementation(libs.grpc.api)

  implementation(libs.log4j.api)
  implementation(libs.log4j.core)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)

  errorprone(libs.errorprone)
}
