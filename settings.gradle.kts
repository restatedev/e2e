rootProject.name = "restate-e2e"
include(
    "functions:counter:contract",
    "functions:counter:impl",
    "test-utils",
    "tests"
)

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
            version("java-sdk", "1.0-SNAPSHOT")

            version("guava", "30.1.1-jre")
            version("grpc", "1.45.1")
            version("assertj", "3.22.0")
            version("log4j", "2.17.2")
            version("javax-annotation", "1.3.2")
            version("junit-jupiter", "5.8.1")
            version("protobuf", "3.20.1")
            version("errorprone", "2.13.1")

            library("restate-sdk", "dev.restate.sdk", "java-sdk").versionRef("java-sdk")

            library("guava", "com.google.guava", "guava").versionRef("guava")

            library("grpc-stub", "io.grpc", "grpc-stub").versionRef("grpc")
            library("grpc-protobuf", "io.grpc", "grpc-protobuf").versionRef("grpc")
            library("grpc-netty-shaded", "io.grpc", "grpc-netty-shaded").versionRef("grpc")

            library("assertj", "org.assertj", "assertj-core").versionRef("assertj")

            library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j")
            library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j")

            library("javax-annotation-api", "javax.annotation", "javax.annotation-api").versionRef("javax-annotation")

            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit-jupiter")

            library("protobuf-java", "com.google.protobuf", "protobuf-java").versionRef("protobuf")
            library("protoc", "com.google.protobuf", "protoc").versionRef("protobuf")

            library("errorprone", "com.google.errorprone", "error_prone_core").versionRef("errorprone")

            plugin("spotless", "com.diffplug.spotless").version("6.5.0")
            plugin("errorprone", "net.ltgt.errorprone").version("2.0.2")
            plugin("protobuf", "com.google.protobuf").version("0.8.18")
            plugin("test-logger", "com.adarshr.test-logger").version("3.2.0")
            plugin("shadowJar", "com.github.johnrengelman.shadow").version("7.1.2")
        }
    }
}
