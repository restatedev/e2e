import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf

plugins {
  java
  alias(libs.plugins.protobuf)
}

dependencies {
  compileOnly(libs.javax.annotation.api)

  api(libs.grpc.stub)
  api(libs.grpc.protobuf)

  protobuf(libs.restate.sdk.core)
}

protobuf {
  protoc {
    // The artifact spec for the Protobuf Compiler
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }

  plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" } }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins { id("grpc") }
      // Generate descriptors
      it.generateDescriptorSet = true
      it.descriptorSetOptions.includeImports = true
      it.descriptorSetOptions.path =
          "${rootProject.projectDir}/.restate/descriptors/${project.name}.descriptor"
    }
  }
}
