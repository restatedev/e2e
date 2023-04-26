package dev.restate.e2e.utils

import java.net.URL
import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName

/** Definition of a function to deploy. */
data class FunctionSpec(
    internal val containerImage: String,
    internal val hostName: String,
    internal val envs: Map<String, String>,
    internal val port: Int,
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
      private var dependencies: MutableList<Startable> = mutableListOf(),
  ) {
    fun withContainerImage(containerImage: String) = apply { this.containerImage = containerImage }

    fun withHostName(hostName: String) = apply { this.hostName = hostName }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    fun withGrpcIngressPort(grpcIngressPort: Int) = apply { this.port = grpcIngressPort }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun dependsOn(container: Startable) = apply { this.dependencies.add(container) }

    fun build() = FunctionSpec(containerImage, hostName, envs, port, dependencies)
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
        .withExposedPorts(port)
  }

  internal fun getFunctionEndpointUrl(): URL {
    return URL("http", this.hostName, this.port, "/")
  }
}
