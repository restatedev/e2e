// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.utils

import java.net.URL
import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName

/** Definition of a service to deploy. */
data class ServiceSpec(
    internal val containerImage: String,
    val hostName: String,
    internal val envs: Map<String, String>,
    internal val port: Int,
    internal val skipRegistration: Boolean,
    internal val dependencies: List<Startable>,
) {

  companion object {
    @JvmStatic
    fun builder(containerImage: String): Builder {
      return Builder(containerImage)
    }
  }

  data class Builder(
      private var containerImage: String,
      private var hostName: String =
          containerImage
              .trim()
              .split(Regex.fromLiteral("/"))
              .last()
              .split(Regex.fromLiteral(":"))
              .first(),
      private var envs: MutableMap<String, String> = mutableMapOf(),
      private var port: Int = 8080,
      private var skipRegistration: Boolean = false,
      private var dependencies: MutableList<Startable> = mutableListOf(),
  ) {
    fun withHostName(hostName: String) = apply { this.hostName = hostName }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    /// Note: the port is not exposed!
    fun withPort(port: Int) = apply { this.port = port }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun skipRegistration() = apply { this.skipRegistration = true }

    fun dependsOn(container: Startable) = apply { this.dependencies.add(container) }

    fun build() = ServiceSpec(containerImage, hostName, envs, port, skipRegistration, dependencies)
  }

  fun toBuilder(): Builder {
    return Builder(
        containerImage,
        hostName,
        envs = envs.toMutableMap(),
        port,
        dependencies = dependencies.toMutableList())
  }

  internal fun toContainer(): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse(containerImage))
        .withEnv("PORT", port.toString())
        .withEnv(envs)
        .dependsOn(dependencies)
  }

  internal fun getEndpointUrl(): URL {
    return URL("http", this.hostName, this.port, "/")
  }
}
