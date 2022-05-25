import com.google.protobuf.gradle.*

plugins {
  `java-library`
  idea
  alias(libs.plugins.protobuf)
}

dependencies {
  api(libs.protobuf.java)
  api(libs.grpc.stub)
  api(libs.grpc.protobuf)

  compileOnly(libs.javax.annotation.api)
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }

  plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" } }

  generateProtoTasks { ofSourceSet("main").forEach { it.plugins { id("grpc") } } }
}
