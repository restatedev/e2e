plugins {
  java
  kotlin("jvm") version "1.6.20"
}

dependencies {
  implementation(libs.slf4j)
  implementation(libs.junit5)
  implementation(platform(libs.testcontainers.bom))
  implementation(libs.testcontainers.core)
  implementation(libs.testcontainers.kafka)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.yaml)

  implementation(libs.grpc.stub)
  implementation(libs.grpc.netty.shaded)
}
