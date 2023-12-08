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
import org.testcontainers.utility.DockerImageName

data class ServiceProjectSpec(
  internal val lambdaArchiveLocation: String,
  internal val containerImage: String,
)

/** Definition of a service to deploy. */
data class ServiceDeployer(
  /**
   * This is going to be the hostname in testcontainers, the lambda name suffix in lambda
   */
  val name: String,
  internal val serviceProjectSpec: ServiceProjectSpec,
  internal val envs: Map<String, String>,
  internal val registrationOptions: RegistrationOptions,
  internal val skipRegistration: Boolean,
) {

  data class RegistrationOptions(
      val additionalHeaders: Map<String, String> = mapOf(),
  )

  companion object {
    @JvmStatic
    fun builder(serviceProjectSpec: ServiceProjectSpec): Builder {
      return Builder(serviceProjectSpec)
    }
  }

  data class Builder(
      private var serviceProjectSpec: ServiceProjectSpec,
      private var name: String =
          serviceProjectSpec.containerImage
              .trim()
              .split(Regex.fromLiteral("/"))
              .last()
              .split(Regex.fromLiteral(":"))
              .first(),
      private var envs: MutableMap<String, String> = mutableMapOf(),
      private var registrationOptions: RegistrationOptions = RegistrationOptions(),
      private var skipRegistration: Boolean = false,
  ) {
    fun withName(hostName: String) = apply { this.name = hostName }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun withRegistrationOptions(registrationOptions: RegistrationOptions) = apply {
      this.registrationOptions = registrationOptions
    }

    fun skipRegistration() = apply { this.skipRegistration = true }

    fun build() =
        ServiceDeployer(
          name,
          serviceProjectSpec,
          envs,
          registrationOptions,
          skipRegistration
        )
  }

  fun toBuilder(): Builder {
    return Builder(
      serviceProjectSpec,
      name,
        envs = envs.toMutableMap())
  }

  internal fun toContainer(): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse(serviceProjectSpec.containerImage))
        .withEnv("PORT", 8080.toString())
        .withEnv(envs)
  }

  internal fun getEndpointUrl(): URL {
    return URL("http", this.name, 8080, "/")
  }
}
