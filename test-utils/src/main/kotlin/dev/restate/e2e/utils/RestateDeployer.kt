package dev.restate.e2e.utils

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.fail
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
    private val additionalEnv: Map<String, String>,
    private val runtimeContainerName: String,
) {

  companion object {
    private const val RESTATE_RUNTIME_CONTAINER_ENV = "RESTATE_RUNTIME_CONTAINER"
    private const val DEFAULT_RUNTIME_CONTAINER =
        "restatedev.jfrog.io/restatedev-docker-local/runtime"

    private const val CONTAINER_LOGS_DIR_ENV = "CONTAINER_LOGS_DIR"

    private const val IMAGE_PULL_POLICY_ENV = "E2E_IMAGE_PULL_POLICY"
    private const val ALWAYS_PULL = "always"

    private const val RUNTIME_GRPC_INGRESS_ENDPOINT = 9090
    private const val RUNTIME_META_ENDPOINT = 8081

    private const val USE_ROCKSDB = "E2E_USE_ROCKSDB"

    private val logger = LogManager.getLogger(RestateDeployer::class.java)

    @JvmStatic
    fun builder(): Builder {
      return Builder()
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
      private var serviceEndpoints: MutableList<FunctionSpec> = mutableListOf(),
      private var additionalContainers: MutableMap<String, GenericContainer<*>> = mutableMapOf(),
      private var additionalEnv: MutableMap<String, String> = mutableMapOf(),
      private var runtimeContainer: String =
          System.getenv(RESTATE_RUNTIME_CONTAINER_ENV) ?: DEFAULT_RUNTIME_CONTAINER,
  ) {

    fun withServiceEndpoint(functionSpec: FunctionSpec) = apply {
      this.serviceEndpoints.add(functionSpec)
    }

    fun withServiceEndpoint(functionSpecBuilder: FunctionSpec.Builder) = apply {
      this.serviceEndpoints.add(functionSpecBuilder.build())
    }

    /** Add a function with default configuration. */
    fun withServiceEndpoint(containerImageName: String) = apply {
      this.withServiceEndpoint(FunctionSpec.builder(containerImageName))
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

    fun withEnv(key: String, value: String) = apply { this.additionalEnv[key] = value }

    fun build() =
        RestateDeployer(
            runtimeDeployments,
            useRocksDb,
            serviceEndpoints,
            additionalContainers,
            additionalEnv,
            runtimeContainer)
  }

  fun deployAll(testClass: Class<*>) {
    deployFunctions(testClass)
    deployAdditionalContainers(testClass)
    deployRuntime(testClass)
  }

  fun deployFunctions(testClass: Class<*>) {
    val testReportDir = resolveContainerTestLogsDir(testClass)

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

  fun deployAdditionalContainers(testClass: Class<*>) {
    val testReportDir = resolveContainerTestLogsDir(testClass)

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

  fun deployRuntime(testClass: Class<*>) {
    // Generate test report directory
    val testReportDir = resolveContainerTestLogsDir(testClass)
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

    if (useRocksDb) {
      fail("Persistence test not yet supported")
    }

    logger.debug("Starting runtime container '{}'", runtimeContainerName)

    // Generate runtime container
    runtimeContainer =
        GenericContainer(DockerImageName.parse(runtimeContainerName))
            .dependsOn(functionContainers.values.map { it.second })
            .dependsOn(additionalContainers.values)
            .withEnv("RUST_LOG", "debug")
            .withEnv("RUST_BACKTRACE", "full")
            .withExposedPorts(RUNTIME_GRPC_INGRESS_ENDPOINT, RUNTIME_META_ENDPOINT)
            .withEnv(additionalEnv)
            .withNetwork(network)
            .withNetworkAliases("runtime")
            .withLogConsumer(ContainerLogger(testReportDir, "restate-runtime"))
            .withCommand("--id 1 --configuration-file /restate.yaml")

    if (System.getenv(IMAGE_PULL_POLICY_ENV) == ALWAYS_PULL) {
      runtimeContainer!!.withImagePullPolicy(PullPolicy.alwaysPull())
    }
    if (useRocksDb) {
      val stateDir = File(tmpDir, "state")
      stateDir.mkdirs()
      runtimeContainer!!.addFileSystemBind(
          stateDir.toString(), "/state", BindMode.READ_WRITE, SelinuxContext.SINGLE)
    }

    runtimeContainer!!.start()

    logger.debug("Restate runtime started and available at {}", getRuntimeGrpcIngressUrl())
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

  fun teardownAll() {
    teardownRuntime()
    teardownAdditionalContainers()
    teardownFunctions()
    network!!.close()
  }

  fun getRuntimeChannel(): ManagedChannel {
    return getRuntimeGrpcIngressUrl().let { url ->
      NettyChannelBuilder.forAddress(url.host, url.port).usePlaintext().build()
    }
  }

  fun getRuntimeGrpcIngressUrl(): URL {
    return runtimeContainer?.getMappedPort(RUNTIME_GRPC_INGRESS_ENDPOINT)?.let {
      URL("http", "127.0.0.1", it, "/")
    }
        ?: throw java.lang.IllegalStateException(
            "Runtime is not configured, as RestateDeployer::deploy has not been invoked")
  }

  fun getRuntimeMetaUrl(): URL {
    return runtimeContainer?.getMappedPort(RUNTIME_META_ENDPOINT)?.let {
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
