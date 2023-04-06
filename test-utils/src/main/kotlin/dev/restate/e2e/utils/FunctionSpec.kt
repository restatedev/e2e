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
    internal val grpcIngressPort: Int,
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
      private var grpcIngressPort: Int = 8080,
      private var dependencies: MutableList<Startable> = mutableListOf(),
  ) {
    fun withContainerImage(containerImage: String) = apply { this.containerImage = containerImage }

    fun withHostName(containerImage: String) = apply { this.containerImage = containerImage }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    fun withGrpcIngressPort(grpcIngressPort: Int) = apply { this.grpcIngressPort = grpcIngressPort }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun dependsOn(container: Startable) = apply { this.dependencies.add(container) }

    fun build() = FunctionSpec(containerImage, hostName, envs, grpcIngressPort, dependencies)
  }

  internal fun toFunctionContainer(): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse(containerImage))
        .withEnv("PORT", grpcIngressPort.toString())
        .withEnv(envs)
        .dependsOn(dependencies)
        .withExposedPorts(grpcIngressPort)
  }

  internal fun getFunctionEndpointUrl(): URL {
    return URL("http", this.hostName, this.grpcIngressPort, "/")
  }
}
