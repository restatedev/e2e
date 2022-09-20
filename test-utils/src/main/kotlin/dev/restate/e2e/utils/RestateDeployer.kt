package dev.restate.e2e.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.grpc.ManagedChannel
import io.grpc.ServiceDescriptor
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.logging.log4j.LogManager
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

class RestateDeployer
private constructor(
    runtimeDeployments: Int,
    private val useRocksDb: Boolean,
    functionSpecs: List<FunctionSpec>,
    private val additionalContainers: Map<String, GenericContainer<*>>,
    private val additionalConfig: Map<String, Any>,
    private val runtimeContainerName: String,
    private val descriptorFile: String?,
) {

  companion object {
    private const val RESTATE_RUNTIME_CONTAINER_ENV = "RESTATE_RUNTIME_CONTAINER"
    private const val DEFAULT_RUNTIME_CONTAINER =
        "restatedev.jfrog.io/restatedev-docker-local/runtime"

    private const val CONTAINER_LOGS_DIR_ENV = "CONTAINER_LOGS_DIR"

    private const val IMAGE_PULL_POLICY_ENV = "E2E_IMAGE_PULL_POLICY"
    private const val ALWAYS_PULL = "always"

    private const val DESCRIPTORS_FILE_ENV = "DESCRIPTORS_FILE"

    private const val RUNTIME_GRPC_ENDPOINT = 8090
    private const val RUNTIME_HTTP_ENDPOINT = 8091

    private const val USE_ROCKSDB = "E2E_USE_ROCKSDB"

    private val logger = LogManager.getLogger(RestateDeployer::class.java)

    @JvmStatic
    fun builder(): Builder {
      return Builder()
    }

    @JvmStatic
    fun generateReportDirFromEnv(testClass: Class<*>): String {
      val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
      val dir =
        Path.of(
          System.getenv(CONTAINER_LOGS_DIR_ENV)!!,
          "${testClass.canonicalName}_${LocalDateTime.now().format(formatter)}")
          .toAbsolutePath()
          .toString()
      File(dir).mkdirs()
      return dir
    }
  }

  private val functionContainers =
      functionSpecs.associate { spec -> spec.hostName to (spec to spec.toFunctionContainer()) }
  private val network = Network.newNetwork()
  private val tmpDir = Files.createTempDirectory("restate-e2e").toFile()
  private var runtimeContainer: GenericContainer<*>? = null

  init {
    assert(runtimeDeployments == 1) { "At the moment only one runtime deployment is supported" }
  }

  data class Builder(
      private var runtimeDeployments: Int = 1,
      private var useRocksDb: Boolean = System.getenv().contains(USE_ROCKSDB),
      private var functions: MutableList<FunctionSpec> = mutableListOf(),
      private var additionalContainers: MutableMap<String, GenericContainer<*>> = mutableMapOf(),
      private var additionalConfig: MutableMap<String, Any> = mutableMapOf(),
      private var runtimeContainer: String =
          System.getenv(RESTATE_RUNTIME_CONTAINER_ENV) ?: DEFAULT_RUNTIME_CONTAINER,
      private var descriptorDirectory: String? = System.getenv(DESCRIPTORS_FILE_ENV),
  ) {

    fun withFunction(functionSpec: FunctionSpec) = apply { this.functions.add(functionSpec) }

    fun withFunction(functionSpecBuilder: FunctionSpec.Builder) = apply {
      this.functions.add(functionSpecBuilder.build())
    }

    /** Add a function with default configuration. */
    fun withFunction(
        containerImageName: String,
        grpcService: ServiceDescriptor,
        vararg otherGrpcServices: ServiceDescriptor
    ) = apply {
      this.withFunction(FunctionSpec.builder(containerImageName, grpcService, *otherGrpcServices))
    }

    fun runtimeDeployments(runtimeDeployments: Int) = apply {
      this.runtimeDeployments = runtimeDeployments
    }

    fun runtimeContainer(runtimeContainer: String) = apply {
      this.runtimeContainer = runtimeContainer
    }

    fun useRocksDB(useRocksDb: Boolean) = apply { this.useRocksDb = useRocksDb }

    /** Add a container that will be added within the same network of functions and runtime. */
    fun withContainer(hostName: String, container: GenericContainer<*>) = apply {
      this.additionalContainers[hostName] = container
    }

    fun withContainer(entry: Pair<String, GenericContainer<*>>) = apply {
      this.additionalContainers[entry.first] = entry.second
    }

    fun withConfigEntries(key: String, value: Any) = apply { this.additionalConfig[key] = value }

    fun build() =
        RestateDeployer(
            runtimeDeployments,
            useRocksDb,
            functions,
            additionalContainers,
            additionalConfig,
            runtimeContainer,
            descriptorDirectory)
  }

  fun deployAll(testReportDir: String) {
    deployFunctions(testReportDir)
    deployAdditionalContainers(testReportDir)
    deployRuntime(testReportDir)
  }

  fun deployFunctions(testReportDir: String) {
    // Deploy functions
    functionContainers.forEach { (functionName, functionContainer) ->
      functionContainer.second.networkAliases = ArrayList()
      functionContainer.second
          .withNetwork(network)
          .withNetworkAliases(functionName)
          .withLogConsumer(ContainerLogger(testReportDir, functionName))
          .start()
      logger.debug(
          "Started function container {} with endpoint {}",
          functionName,
          functionContainer.first.getFunctionEndpointUrl())
    }
  }

  fun deployAdditionalContainers(testReportDir: String) {
    // Deploy additional containers
    additionalContainers.forEach { (containerHost, container) ->
      container.networkAliases = ArrayList()
      container
          .withNetwork(network)
          .withNetworkAliases(containerHost)
          .withLogConsumer(ContainerLogger(testReportDir, containerHost))
          .start()
      logger.debug("Started container {} with image {}", containerHost, container.dockerImageName)
    }
  }

  fun deployRuntime(testReportDir: String) {
    // Generate test report directory
    logger.debug("Writing container logs to {}", testReportDir)

    // Deploy additional containers
    additionalContainers.forEach { (containerHost, container) ->
      container.networkAliases = ArrayList()
      container
          .withNetwork(network)
          .withNetworkAliases(containerHost)
          .withLogConsumer(ContainerLogger(testReportDir, containerHost))
          .start()
      logger.debug("Started container {} with image {}", containerHost, container.dockerImageName)
    }

    val configFile = File(tmpDir, "restate.yaml")

    val mapper = ObjectMapper(YAMLFactory())

    val config = mutableMapOf<String, Any>()
    config["peers"] = listOf("127.0.0.1:9001")
    if (useRocksDb) {
      config["consensus"] =
          mapOf("storage_type" to mapOf("RocksDb" to mapOf("working_directory" to "/state")))
    } else {
      config["consensus"] = mapOf("storage_type" to "Memory")
    }
    config["grpc_port"] = RUNTIME_GRPC_ENDPOINT
    config["services"] = functionContainers.values.flatMap { it.first.toManifests(mapper) }
    if (descriptorFile != null) {
      config["http_endpoint"] =
          mapOf("port" to RUNTIME_HTTP_ENDPOINT, "descriptors_path" to "/contracts.descriptor")
    }
    config.putAll(additionalConfig)

    mapper.writeValue(configFile, config)

    logger.debug("Written config to {}", configFile)
    logger.debug("Starting runtime container '{}'", runtimeContainerName)

    // Generate runtime container
    runtimeContainer =
        GenericContainer(DockerImageName.parse(runtimeContainerName))
            .dependsOn(functionContainers.values.map { it.second })
            .dependsOn(additionalContainers.values)
            .withEnv("RUST_LOG", "debug")
            .withEnv("RUST_BACKTRACE", "full")
            .withExposedPorts(RUNTIME_GRPC_ENDPOINT)
            .withNetwork(network)
            .withNetworkAliases("runtime")
            .withLogConsumer(ContainerLogger(testReportDir, "restate-runtime"))
            .withCopyFileToContainer(
                MountableFile.forHostPath(configFile.toPath()), "/restate.yaml")
            .withCommand("--id 1 --configuration-file /restate.yaml")

    if (System.getenv(IMAGE_PULL_POLICY_ENV) == ALWAYS_PULL) {
      runtimeContainer!!.withImagePullPolicy(PullPolicy.alwaysPull())
    }
    if (descriptorFile != null) {
      runtimeContainer!!
          .withCopyFileToContainer(
              MountableFile.forHostPath(descriptorFile), "/contracts.descriptor")
          .addExposedPort(RUNTIME_HTTP_ENDPOINT)
    }
    if (useRocksDb) {
      val stateDir = File(tmpDir, "state")
      stateDir.mkdirs()
      runtimeContainer!!.addFileSystemBind(
          stateDir.toString(), "/state", BindMode.READ_WRITE, SelinuxContext.SINGLE)
    }

    runtimeContainer!!.start()

    logger.debug("Restate runtime started and available at {}", getRuntimeGrpcEndpointUrl())
  }

  fun teardownAdditionalContainers() {
    additionalContainers.forEach { (_, container) -> container.stop() }
  }

  fun teardownFunctions() {
    functionContainers.forEach { (_, container) -> container.second.stop() }
  }

  fun teardownRuntime() {
    runtimeContainer!!.stop()
  }

  fun killRuntime() {
    runtimeContainer!!.dockerClient.killContainerCmd(runtimeContainer!!.containerId)
    runtimeContainer!!.stop()
  }

  fun teardownAll() {
    teardownRuntime()
    teardownAdditionalContainers()
    teardownFunctions()
    network!!.close()
  }

  private fun getRuntimeGrpcEndpointUrl(): URL {
    return runtimeContainer?.getMappedPort(RUNTIME_GRPC_ENDPOINT)?.let {
      URL("http", "127.0.0.1", it, "/")
    }
        ?: throw java.lang.IllegalStateException(
            "Runtime is not configured, as RestateDeployer::deploy has not been invoked")
  }

  fun createRuntimeChannel(): ManagedChannel {
    return getRuntimeGrpcEndpointUrl().let { url ->
      NettyChannelBuilder.forAddress(url.host, url.port).usePlaintext().build()
    }
  }

  fun getRuntimeHttpEndpointUrl(): URL {
    checkNotNull(descriptorFile) {
      "No descriptor directory is configured to start the HTTP Endpoint. " +
          "Make sure when running tests you have the $DESCRIPTORS_FILE_ENV environment variable correctly configured"
    }
    return runtimeContainer?.getMappedPort(RUNTIME_HTTP_ENDPOINT)?.let {
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

  private fun resolveContainerTestLogsDir(testClass: Class<*>): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    val dir =
        Path.of(
                System.getenv(CONTAINER_LOGS_DIR_ENV)!!,
                "${testClass.canonicalName}_${LocalDateTime.now().format(formatter)}")
            .toAbsolutePath()
            .toString()
    File(dir).mkdirs()
    return dir
  }
}
