// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import com.bmuschko.gradle.docker.tasks.image.*

plugins {
  id("com.bmuschko.docker-remote-api") version "9.3.1"
  id("com.github.node-gradle.node") version "4.0.0"
}

tasks.register("updateRestateDependency") {
  mustRunAfter("npmInstall")
  doLast { exec { commandLine("npm", "update", "@restatedev/restate-sdk") } }
}

tasks.register<Copy>("prepareDockerBuild") {
  mustRunAfter("npmInstall")
  if (!System.getenv("SDK_TYPESCRIPT_LOCAL_BUILD").isNullOrEmpty()) {
    dependsOn("installLocalSdkTypescript")
  } else {
    dependsOn("updateRestateDependency")
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
  destinationDir = layout.buildDirectory.dir("docker").get().asFile
}

tasks.create<DockerBuildImage>("dockerBuild") {
  dependsOn("prepareDockerBuild")
  images.add("restatedev/e2e-node-services")
}

tasks.named("check") {
  mustRunAfter("npmInstall")
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
          commandLine("npm", "run", "build")
        }
        .assertNormalExitValue()
    exec {
          workingDir = sdkTypescriptDirectory
          commandLine("npm", "--workspaces", "pack")
        }
        .assertNormalExitValue()

    copy {
      from(sdkTypescriptDirectory)
      include("*restate-sdk*.tgz")
      into(".")
    }

    fileTree(sdkTypescriptDirectory)
        .matching { include("*restate-sdk*.tgz") }
        .forEach { delete(it) }

    val deps = fileTree(projectDir).matching { include("*.tgz") }.toList()
    exec {
      workingDir = projectDir
      commandLine("npm", "install", "--save", *deps.toTypedArray())
    }
  }
}
