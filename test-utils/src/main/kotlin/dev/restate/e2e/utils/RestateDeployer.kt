package dev.restate.e2e.utils

import java.io.File
import java.net.URL
import java.nio.file.Files
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

class RestateDeployer
private constructor(runtimeDeployments: Int, functions: List<FunctionContainer>) {

  companion object {
    private const val RUNTIME_CONTAINER = "ghcr.io/restatedev/runtime:main"
    private const val RUNTIME_GRPC_ENDPOINT = 8090

    private const val IMAGE_PULL_POLICY = "E2E_IMAGE_PULL_POLICY"
    private const val ALWAYS_PULL = "always"

    private val logger = LoggerFactory.getLogger(RestateDeployer::class.java)
  }

  private val functionContainers = functions.associate { fn -> fn.name to fn.toGenericContainer() }
  private var runtimeContainer: GenericContainer<*>? = null

  init {
    assert(functionContainers.size == 1) {
      "At the moment you MUST specify exactly one function container"
    }
    assert(runtimeDeployments == 1) { "At the moment only one runtime deployment is supported" }
  }

  data class FunctionContainer(val name: String) {
    internal fun toGenericContainer(): GenericContainer<*> {
      return GenericContainer(DockerImageName.parse("restatedev/$name"))
          .withEnv("PORT", "8080")
          .withLogConsumer(Slf4jLogConsumer(logger).withPrefix("function-$name"))
          .withExposedPorts(8080)
    }
  }

  data class Builder(
      var runtimeDeployments: Int = 1,
      var functions: MutableList<FunctionContainer> = mutableListOf()
  ) {

    fun function(functionContainerName: String) = apply {
      this.functions.add(FunctionContainer(functionContainerName))
    }

    fun function(functionContainer: FunctionContainer) = apply {
      this.functions.add(functionContainer)
    }

    fun runtimeDeployments(runtimeDeployments: Int) = apply {
      this.runtimeDeployments = runtimeDeployments
    }

    fun build() = RestateDeployer(runtimeDeployments, functions)
  }

  fun deploy() {
    val network = Network.newNetwork()

    // Deploy functions
    functionContainers.forEach { (name, genericContainer) ->
      genericContainer.withNetwork(network).withNetworkAliases(name).start()
      logger.debug(
          "Started function container {} with endpoint {}", name, getFunctionEndpointUrl(name))
    }

    // Generate config and write to temp dir
    val config =
        """
        |peers:
        |  - 127.0.0.1:9001
        |
        |grpc_port: $RUNTIME_GRPC_ENDPOINT
        |
        |consensus:
        |  storage_type: Memory
        |  
        |function_endpoint: ${getFunctionEndpointUrl(functionContainers.keys.first())}
        """.trimMargin(
            "|")

    val configFile =
        File(Files.createTempDirectory("restate-e2e").toFile(), "restate.yaml").toPath()
    Files.writeString(configFile, config)
    logger.debug("Written config to {}", configFile)

    // Generate runtime container
    runtimeContainer =
        GenericContainer(DockerImageName.parse(RUNTIME_CONTAINER))
            .dependsOn(functionContainers.values)
            .withEnv("RUST_LOG", "debug")
            .withExposedPorts(RUNTIME_GRPC_ENDPOINT)
            .withNetwork(network)
            .withNetworkAliases("runtime")
            .withLogConsumer(Slf4jLogConsumer(logger).withPrefix("runtime"))
            .withCopyFileToContainer(MountableFile.forHostPath(configFile), "/restate.yaml")
            .withCommand("--id 1 --configuration-file /restate.yaml")

    if (System.getenv(IMAGE_PULL_POLICY) == ALWAYS_PULL) {
      runtimeContainer!!.withImagePullPolicy(PullPolicy.alwaysPull())
    }

    runtimeContainer!!.start()
  }

  fun teardown() {
    runtimeContainer!!.stop()
    functionContainers.forEach { (_, container) -> container.stop() }
  }

  fun getFunctionEndpointUrl(name: String): URL {
    return URL("http", name, 8080, "/")
  }

  fun getRuntimeFunctionEndpointUrl(_name: String): URL {
    return runtimeContainer?.getMappedPort(RUNTIME_GRPC_ENDPOINT)?.let {
      URL("http", "127.0.0.1", it, "/")
    }
        ?: throw java.lang.IllegalStateException(
            "Runtime is not configured, as RestateDeployer::deploy has not been invoked")
  }
}
