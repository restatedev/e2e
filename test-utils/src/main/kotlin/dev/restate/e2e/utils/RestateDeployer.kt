package dev.restate.e2e.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

class RestateDeployer
private constructor(
    runtimeDeployments: Int,
    functions: List<FunctionContainer>,
    private val additionalContainers: Map<String, GenericContainer<*>>,
    private val additionalConfig: Map<String, Any>
) {

  companion object {
    private const val RUNTIME_CONTAINER = "ghcr.io/restatedev/runtime:main"
    private const val RUNTIME_GRPC_ENDPOINT = 8090

    private const val IMAGE_PULL_POLICY = "E2E_IMAGE_PULL_POLICY"
    private const val ALWAYS_PULL = "always"

    private val logger = LoggerFactory.getLogger(RestateDeployer::class.java)
  }

  private val functionContainers = functions.associate { fn -> fn.name to fn.toGenericContainer() }
  private var runtimeContainer: GenericContainer<*>? = null
  private var network: Network? = null

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
          .withExposedPorts(8080)
    }
  }

  data class Builder(
      var runtimeDeployments: Int = 1,
      var functions: MutableList<FunctionContainer> = mutableListOf(),
      var additionalContainers: MutableMap<String, GenericContainer<*>> = mutableMapOf(),
      var additionalConfig: MutableMap<String, Any> = mutableMapOf()
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

    /** Add a container that will be added within the same network of functions and runtime. */
    fun withContainer(hostName: String, container: GenericContainer<*>) = apply {
      this.additionalContainers[hostName] = container
    }

    fun withConfigEntries(key: String, value: Any) = apply { this.additionalConfig[key] = value }

    fun build() =
        RestateDeployer(runtimeDeployments, functions, additionalContainers, additionalConfig)
  }

  fun deploy(testClass: Class<*>) {
    // Generate test report directory
    val testReportDir = computeContainerTestLogsDir(testClass).toAbsolutePath().toString()
    check(File(testReportDir).mkdirs()) { "Cannot create test report directory $testReportDir" }

    network = Network.newNetwork()

    // Deploy functions
    functionContainers.forEach { (functionName, container) ->
      container.networkAliases = ArrayList()
      container.withNetwork(network).withNetworkAliases(functionName)
        .withLogConsumer(ContainerLogger(testReportDir, functionName))
        .start()
      logger.debug(
          "Started function container {} with endpoint {}",
          functionName,
          getFunctionEndpointUrl(functionName))
    }

    // Deploy additional containers
    additionalContainers.forEach { (containerHost, container) ->
      container.networkAliases = ArrayList()
      container
          .withNetwork(network)
          .withNetworkAliases(containerHost)
          .withLogConsumer(Slf4jLogConsumer(logger).withPrefix(containerHost))
          .start()
      logger.debug("Started container {} with image {}", containerHost, container.dockerImageName)
    }

    val configFile = File(Files.createTempDirectory("restate-e2e").toFile(), "restate.yaml")

    val config = mutableMapOf<String, Any>()
    config["peers"] = listOf("127.0.0.1:9001")
    config["consensus"] = mutableMapOf("storage_type" to "Memory")
    config["grpc_port"] = RUNTIME_GRPC_ENDPOINT
    config["function_endpoint"] = getFunctionEndpointUrl(functionContainers.keys.first())
    config.putAll(additionalConfig)

    val mapper = ObjectMapper(YAMLFactory())
    mapper.writeValue(configFile, config)

    logger.debug("Written config to {}", configFile)

    // Generate runtime container
    runtimeContainer =
        GenericContainer(DockerImageName.parse(RUNTIME_CONTAINER))
            .dependsOn(functionContainers.values)
            .dependsOn(additionalContainers.values)
            .withEnv("RUST_LOG", "debug")
            .withExposedPorts(RUNTIME_GRPC_ENDPOINT)
            .withNetwork(network)
            .withNetworkAliases("runtime")
            .withLogConsumer(ContainerLogger(testReportDir, "restate-runtime"))
            .withCopyFileToContainer(MountableFile.forHostPath(configFile.toPath()), "/restate.yaml")
            .withCommand("--id 1 --configuration-file /restate.yaml")

    if (System.getenv(IMAGE_PULL_POLICY) == ALWAYS_PULL) {
      runtimeContainer!!.withImagePullPolicy(PullPolicy.alwaysPull())
    }

    runtimeContainer!!.start()
  }

  fun teardown() {
    runtimeContainer!!.stop()
    additionalContainers.forEach { (_, container) -> container.stop() }
    functionContainers.forEach { (_, container) -> container.stop() }
    network!!.close()
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

  fun getAdditionalContainerExposedPort(hostName: String, port: Int): String {
    return additionalContainers[hostName]?.let { "${it.host}:${it.getMappedPort(port)}" }
        ?: throw java.lang.IllegalStateException(
            "Requested additional container with hostname $hostName is not registered")
  }

  private fun computeContainerTestLogsDir(testClass: Class<*>): Path {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    return Path.of(
        System.getenv("CONTAINER_LOGS_DIR")!!,
        "${testClass.canonicalName}_${LocalDateTime.now().format(formatter)}")
  }
}
