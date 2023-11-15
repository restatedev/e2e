import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf

plugins {
  java
  kotlin("jvm")
  alias(libs.plugins.protobuf)
}

dependencies {
  compileOnly(libs.javax.annotation.api)

  api(libs.protobuf.java)
  api(libs.protobuf.kotlin)
  api(libs.grpc.stub)
  api(libs.grpc.protobuf)
  api(libs.grpc.kotlin.stub) { exclude("javax.annotation", "javax.annotation-api") }

  protobuf(libs.restate.sdk.core)
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }

  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" }
    id("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpckt.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins { id("kotlin") }
    }
  }
}
