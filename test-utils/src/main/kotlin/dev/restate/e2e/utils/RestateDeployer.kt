package dev.restate.e2e.utils

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.File
import java.lang.reflect.Method
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName

class RestateDeployer
private constructor(
    runtimeDeployments: Int,
    functionSpecs: List<FunctionSpec>,
    private val additionalContainers: Map<String, GenericContainer<*>>,
    private val additionalEnv: Map<String, String>,
    private val runtimeContainerName: String,
) : AutoCloseable, ExtensionContext.Store.CloseableResource {

  companion object {
    private const val RESTATE_RUNTIME_CONTAINER_ENV = "RESTATE_RUNTIME_CONTAINER"
    private const val DEFAULT_RUNTIME_CONTAINER = "ghcr.io/restatedev/restate"

    private const val CONTAINER_LOGS_DIR_ENV = "CONTAINER_LOGS_DIR"

    private const val IMAGE_PULL_POLICY_ENV = "E2E_IMAGE_PULL_POLICY"
    private const val ALWAYS_PULL = "always"

    private val logger = LogManager.getLogger(RestateDeployer::class.java)

    @JvmStatic
    fun builder(): Builder {
      return Builder()
    }

    @JvmStatic
    fun reportDirectory(testClass: Class<*>): String {
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

    @JvmStatic
    fun reportDirectory(testClass: Class<*>, testMethod: Method): String {
      val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
      val dir =
          Path.of(
                  System.getenv(CONTAINER_LOGS_DIR_ENV)!!,
                  "${testClass.canonicalName}_${testMethod.name}_${LocalDateTime.now().format(formatter)}")
              .toAbsolutePath()
              .toString()
      File(dir).mkdirs()
      return dir
    }
  }

  private val functionContainers =
      functionSpecs.associate { spec -> spec.hostName to (spec to spec.toContainer()) }
  private val network = Network.newNetwork()
  private val tmpDir = Files.createTempDirectory("restate-e2e").toFile()
  private val runtimeContainer = GenericContainer(DockerImageName.parse(runtimeContainerName))
  private val deployedContainers =
      mapOf(RESTATE_RUNTIME to runtimeContainer) +
          functionContainers.map { it.key to it.value.second } +
          additionalContainers

  init {
    assert(runtimeDeployments == 1) { "At the moment only one runtime deployment is supported" }
  }

  data class Builder(
      private var runtimeDeployments: Int = 1,
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

    /** Add a container that will be added within the same network of functions and runtime. */
    fun withContainer(hostName: String, container: GenericContainer<*>) = apply {
      this.additionalContainers[hostName] = container
    }

    fun withContainer(entry: Pair<String, GenericContainer<*>>) = apply {
      this.additionalContainers[entry.first] = entry.second
    }

    fun withEnv(key: String, value: String) = apply { this.additionalEnv[key] = value }

    fun withEnv(map: Map<String, String>) = apply { this.additionalEnv.putAll(map) }

    fun build() =
        RestateDeployer(
            runtimeDeployments,
            serviceEndpoints,
            additionalContainers,
            additionalEnv,
            runtimeContainer)
  }

  fun deployAll(testReportDir: String) {
    deployFunctions(testReportDir)
    deployAdditionalContainers(testReportDir)
    deployRuntime(testReportDir)
  }

  private fun deployFunctions(testReportDir: String) {
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

  private fun deployAdditionalContainers(testReportDir: String) {
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

  private fun deployRuntime(testReportDir: String) {
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

    // Prepare state directory to mount
    val stateDir = File(tmpDir, "state")
    stateDir.mkdirs()

    logger.debug("Starting runtime container '{}'", runtimeContainerName)

    // Configure runtime container
    runtimeContainer
        .dependsOn(functionContainers.values.map { it.second })
        .dependsOn(additionalContainers.values)
        .withExposedPorts(RUNTIME_META_ENDPOINT_PORT, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT)
        .withEnv(additionalEnv)
        // These envs should not be overriden by additionalEnv
        .withEnv("RESTATE_META__STORAGE_PATH", "/state/meta")
        .withEnv("RESTATE_WORKER__STORAGE_ROCKSDB__PATH", "/state/worker")
        .withEnv("RESTATE_META__REST_ADDRESS", "0.0.0.0:${RUNTIME_META_ENDPOINT_PORT}")
        .withEnv(
            "RESTATE_WORKER__INGRESS_GRPC__BIND_ADDRESS",
            "0.0.0.0:${RUNTIME_GRPC_INGRESS_ENDPOINT_PORT}")
        .withNetwork(network)
        .withNetworkAliases(RESTATE_RUNTIME)
        .withLogConsumer(ContainerLogger(testReportDir, "restate-runtime"))

    // Mount state directory
    runtimeContainer.addFileSystemBind(
        stateDir.toString(), "/state", BindMode.READ_WRITE, SelinuxContext.SINGLE)

    if (System.getenv(IMAGE_PULL_POLICY_ENV) == ALWAYS_PULL) {
      runtimeContainer.withImagePullPolicy(PullPolicy.alwaysPull())
    }

    runtimeContainer.start()

    logger.debug(
        "Restate runtime started. gRPC ingress port: {}. Meta REST API port: {}",
        getContainerPort(RESTATE_RUNTIME, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT),
        getContainerPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT))
    logger.debug("Runtime container id {}", runtimeContainer.containerId)

    // Let's execute service discovery to register the services
    functionContainers.values.forEach { (spec, _) -> discoverServiceEndpoint(spec) }
  }

  @Serializable
  private data class RegisterServiceEndpointRequest(
      val uri: String,
      val additionalHeaders: Map<String, String>? = null,
      val retryPolicy: FunctionSpec.RetryPolicy? = null
  )

  @OptIn(ExperimentalSerializationApi::class)
  fun discoverServiceEndpoint(spec: FunctionSpec) {
    val url = spec.getFunctionEndpointUrl()

    // Can replace with code generated from the openapi contract
    val jsonEncoder = Json { namingStrategy = JsonNamingStrategy.SnakeCase }
    val body =
        jsonEncoder.encodeToString(
            RegisterServiceEndpointRequest(
                uri = url.toString(),
                additionalHeaders = spec.registrationOptions.additionalHeaders,
                retryPolicy = spec.registrationOptions.retryPolicy))
    logger.debug("Going to request discovery of endpoint: {}", body)

    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:${getContainerPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT)}/endpoint/discover"))
            .POST(BodyPublishers.ofString(body))
            .headers("Content-Type", "application/json")
            .build()

    val response = client.send(req, BodyHandlers.ofString())

    if (response.statusCode() != 200) {
      fail(
          "Error when discovering endpoint $url, " +
              "got status code ${response.statusCode()} with body: ${response.body()}")
    }

    logger.debug(
        "Successfully executed discovery for endpoint {}. Result: {}", url, response.body())
  }

  private fun teardownAdditionalContainers() {
    additionalContainers.forEach { (_, container) -> container.stop() }
  }

  private fun teardownFunctions() {
    functionContainers.forEach { (_, container) -> container.second.stop() }
  }

  private fun teardownRuntime() {
    runtimeContainer.stop()
  }

  internal fun teardownAll() {
    teardownRuntime()
    teardownAdditionalContainers()
    teardownFunctions()
    network!!.close()
  }

  internal fun createRuntimeChannel(): ManagedChannel {
    return getContainerPort(RESTATE_RUNTIME, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT).let { port ->
      NettyChannelBuilder.forAddress("127.0.0.1", port).disableRetry().usePlaintext().build()
    }
  }

  internal fun getContainerPort(hostName: String, port: Int): Int {
    return deployedContainers[hostName]?.getMappedPort(port)
        ?: throw java.lang.IllegalStateException(
            "Requested port for container $hostName, but the container or the port was not found")
  }

  fun getContainerHandle(hostName: String): ContainerHandle {
    return ContainerHandle(
        deployedContainers[hostName]
            ?: throw java.lang.IllegalArgumentException("Cannot find container $hostName"))
  }

  override fun close() {
    teardownAll()
  }
}
