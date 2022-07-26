rootProject.name = "restate-e2e"

include(
    "functions:collections:contract",
    "functions:collections:impl",
    "functions:coordinator:contract",
    "functions:coordinator:impl",
    "functions:counter:contract",
    "functions:counter:impl",
    "functions:externalcall:contract",
    "functions:externalcall:impl",
    "functions:errors:contract",
    "functions:errors:impl",
    "functions:http-server",
    "functions:utils",
    "test-utils",
    "tests")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven {
      url = uri("https://maven.pkg.github.com/restatedev/java-sdk")
      credentials {
        username = System.getenv("GH_PACKAGE_READ_ACCESS_USER")
        password = System.getenv("GH_PACKAGE_READ_ACCESS_TOKEN")
      }
    }
  }

  versionCatalogs {
    create("libs") {
      // Versions
      version("java-sdk", "1.0-SNAPSHOT")

      version("guava", "30.1.1-jre")
      version("protobuf", "3.21.2")
      version("grpc", "1.47.0")

      version("log4j", "2.17.2")
      version("javax-annotation", "1.3.2")

      version("jackson", "2.13.3")

      version("cloudevents", "2.3.0")

      version("junit-jupiter", "5.8.2")
      version("assertj", "3.22.0")
      version("testcontainers", "1.17.2")
      version("awaitility", "4.2.0")

      version("errorprone", "2.13.1")

      // Libraries
      library("restate-sdk", "dev.restate.sdk", "java-sdk").versionRef("java-sdk")

      library("guava", "com.google.guava", "guava").versionRef("guava")
      library("protoc", "com.google.protobuf", "protoc").versionRef("protobuf")
      library("grpc-stub", "io.grpc", "grpc-stub").versionRef("grpc")
      library("grpc-protobuf", "io.grpc", "grpc-protobuf").versionRef("grpc")
      library("grpc-netty-shaded", "io.grpc", "grpc-netty-shaded").versionRef("grpc")

      library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j")
      library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j")
      library("javax-annotation-api", "javax.annotation", "javax.annotation-api")
          .versionRef("javax-annotation")

      library("jackson-bom", "com.fasterxml.jackson", "jackson-bom").versionRef("jackson")
      library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").withoutVersion()
      library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind").withoutVersion()
      library("jackson-yaml", "com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml")
          .withoutVersion()

      library("cloudevents-bom", "io.cloudevents", "cloudevents-bom").versionRef("cloudevents")
      library("cloudevents-core", "io.cloudevents", "cloudevents-core").withoutVersion()
      library("cloudevents-kafka", "io.cloudevents", "cloudevents-kafka").withoutVersion()
      library("cloudevents-json", "io.cloudevents", "cloudevents-json-jackson").withoutVersion()

      library("junit-all", "org.junit.jupiter", "junit-jupiter").versionRef("junit-jupiter")
      library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit-jupiter")
      library("assertj", "org.assertj", "assertj-core").versionRef("assertj")
      library("testcontainers-bom", "org.testcontainers", "testcontainers-bom")
          .versionRef("testcontainers")
      library("testcontainers-core", "org.testcontainers", "testcontainers").withoutVersion()
      library("testcontainers-kafka", "org.testcontainers", "kafka").withoutVersion()
      library("awaitility", "org.awaitility", "awaitility-kotlin").versionRef("awaitility")

      library("errorprone", "com.google.errorprone", "error_prone_core").versionRef("errorprone")

      // Plugins
      plugin("spotless", "com.diffplug.spotless").version("6.6.1")
      plugin("errorprone", "net.ltgt.errorprone").version("2.0.2")
      plugin("protobuf", "com.google.protobuf").version("0.8.18")
      plugin("test-logger", "com.adarshr.test-logger").version("3.2.0")
      plugin("shadowJar", "com.github.johnrengelman.shadow").version("7.1.2")
      plugin("jib", "com.google.cloud.tools.jib").version("3.2.1")
    }
  }
}

// Include composite build for easier local testing
if (!System.getenv("E2E_LOCAL_BUILD").isNullOrEmpty()) {
  includeBuild("../java-sdk") {
    dependencySubstitution { substitute(module("dev.restate.sdk:java-sdk")).using(project(":sdk")) }
  }
}
