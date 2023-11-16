import com.bmuschko.gradle.docker.tasks.image.*

plugins {
  id("com.bmuschko.docker-remote-api") version "9.3.1"
  id("com.github.node-gradle.node") version "4.0.0"
}

tasks.register("generateProto") {
  mustRunAfter("npmInstall")
  dependsOn("npm_run_proto")
}

tasks.register<Copy>("prepareDockerBuild") {
  dependsOn("generateProto")

  if (!System.getenv("SDK_TYPESCRIPT_LOCAL_BUILD").isNullOrEmpty()) {
    dependsOn("installLocalSdkTypescript")
  }

  from(".") {
    include(
        "src/**",
        "*.tgz",
        ".dockerignore",
        ".eslintignore",
        ".eslintrc.json",
        "package.json",
        "package-lock.json",
        "tsconfig.json",
        "Dockerfile")
    into(".")
  }
  destinationDir = file("${buildDir}/docker")
}

tasks.create<DockerBuildImage>("dockerBuild") {
  dependsOn("prepareDockerBuild")
  images.add("restatedev/e2e-node-services")
}

tasks.named("check") {
  mustRunAfter("npmInstall", "generateProto")
  dependsOn("npm_run_lint")
}

tasks.register("installLocalSdkTypescript") {
  val sdkTypescriptDirectory =
      file(properties["sdkTypescriptLocation"] ?: "${rootDir}/../sdk-typescript")
  check(sdkTypescriptDirectory.exists()) {
    "Cannot find the typescript directory. Looking in ${sdkTypescriptDirectory.toPath()}"
  }

  doLast {
    exec {
          workingDir = sdkTypescriptDirectory
          commandLine("npm", "install")
        }
        .assertNormalExitValue()
    exec {
          workingDir = sdkTypescriptDirectory
          commandLine("npm", "run", "proto")
        }
        .assertNormalExitValue()
    exec {
          workingDir = sdkTypescriptDirectory
          commandLine("npm", "run", "build")
        }
        .assertNormalExitValue()
    exec {
          workingDir = sdkTypescriptDirectory
          commandLine("npm", "pack")
        }
        .assertNormalExitValue()

    copy {
      from(sdkTypescriptDirectory)
      include("*.tgz")
      into(".")
    }

    val packageToInstall =
        fileTree(projectDir).matching { include("*.tgz") }.maxByOrNull { it.lastModified() }!!
    println("Going to install $packageToInstall")

    exec {
          workingDir = projectDir
          commandLine("npm", "install", "--save", packageToInstall)
        }
        .assertNormalExitValue()
  }
}
