package dev.restate.e2e.utils

import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName

/** Definition of a function to deploy. */
data class FunctionSpec(
    internal val containerImage: String,
    internal val hostName: String,
    internal val service_endpoints: List<String>,
    internal val envs: Map<String, String>,
    internal val grpcEndpointPort: Int,
    internal val dependencies: List<Startable>,
) {

  companion object {
    @JvmStatic
    fun builder(
        containerImage: String,
        grpcServiceName: String,
        vararg otherGrpcServices: String
    ): Builder {
      return Builder(
          containerImage, mutableListOf(grpcServiceName).apply { addAll(otherGrpcServices) })
    }
  }

  init {
    assert(service_endpoints.isNotEmpty()) {
      "FunctionSpec must contain at least one service endpoint"
    }
  }

  data class Builder(
      private var containerImage: String,
      private var service_endpoints: MutableList<String>,
      private var hostName: String =
          containerImage
              .trim()
              .split(Regex.fromLiteral("/"))
              .last()
              .split(Regex.fromLiteral(":"))
              .first(),
      private var envs: MutableMap<String, String> = mutableMapOf(),
      private var grpcEndpointPort: Int = 8080,
      private var dependencies: MutableList<Startable> = mutableListOf(),
  ) {
    fun withContainerImage(containerImage: String) = apply { this.containerImage = containerImage }

    fun withHostName(containerImage: String) = apply { this.containerImage = containerImage }

    fun withServices(vararg services: String) = apply { this.service_endpoints.addAll(services) }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    fun withGrpcEndpointPort(grpcEndpointPort: Int) = apply {
      this.grpcEndpointPort = grpcEndpointPort
    }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun dependsOn(container: Startable) = apply { this.dependencies.add(container) }

    fun build() =
        FunctionSpec(
            containerImage, hostName, service_endpoints, envs, grpcEndpointPort, dependencies)
  }

  internal fun toFunctionContainer(): Pair<FunctionSpec, GenericContainer<*>> {
    return this to
        GenericContainer(DockerImageName.parse(containerImage))
            .withEnv("PORT", grpcEndpointPort.toString())
            .withEnv(envs)
            .dependsOn(dependencies)
            .withExposedPorts(grpcEndpointPort)
  }
}
