package dev.restate.e2e.utils

import java.net.URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName

/** Definition of a service to deploy. */
data class ServiceSpec(
    internal val containerImage: String,
    internal val hostName: String,
    internal val envs: Map<String, String>,
    internal val port: Int,
    internal val registrationOptions: RegistrationOptions,
    internal val dependencies: List<Startable>,
) {

  // Perhaps at some point we could autogenerate these from the openapi doc and also remove the need
  // to manually implement these serialization routines
  @Serializable
  sealed class RetryPolicy {

    @Serializable @SerialName("None") object None : RetryPolicy()

    @Serializable
    @SerialName("FixedDelay")
    class FixedDelay(val interval: String, val maxAttempts: Int) : RetryPolicy()
  }

  data class RegistrationOptions(
      val additionalHeaders: Map<String, String> = mapOf(),
      val retryPolicy: RetryPolicy? = null
  )

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
      private var registrationOptions: RegistrationOptions = RegistrationOptions(),
      private var dependencies: MutableList<Startable> = mutableListOf(),
  ) {
    fun withHostName(hostName: String) = apply { this.hostName = hostName }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    fun withPort(port: Int) = apply { this.port = port }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun withRegistrationOptions(registrationOptions: RegistrationOptions) = apply {
      this.registrationOptions = registrationOptions
    }

    fun dependsOn(container: Startable) = apply { this.dependencies.add(container) }

    fun build() =
        ServiceSpec(containerImage, hostName, envs, port, registrationOptions, dependencies)
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
