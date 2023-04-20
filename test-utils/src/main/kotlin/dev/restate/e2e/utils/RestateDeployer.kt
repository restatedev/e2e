package dev.restate.e2e.utils

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.File
import java.net.URI
import java.net.URL
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
      functionSpecs.associate { spec -> spec.hostName to (spec to spec.toContainer()) }
  private val network = Network.newNetwork()
  private val tmpDir = Files.createTempDirectory("restate-e2e").toFile()
  private var runtimeContainer: GenericContainer<*>? = null

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

    // Prepare state directory to mount
    val stateDir = File(tmpDir, "state")
    stateDir.mkdirs()

    logger.debug("Starting runtime container '{}'", runtimeContainerName)

    // Generate runtime container
    runtimeContainer =
        GenericContainer(DockerImageName.parse(runtimeContainerName))
            .dependsOn(functionContainers.values.map { it.second })
            .dependsOn(additionalContainers.values)
            .withEnv("RUST_LOG", "debug")
            .withEnv("RUST_BACKTRACE", "full")
            .withExposedPorts(RUNTIME_META_ENDPOINT, RUNTIME_GRPC_INGRESS_ENDPOINT)
            .withEnv(additionalEnv)
            // These envs should not be overriden by additionalEnv
            .withEnv("RESTATE_META__STORAGE_PATH", "/state/meta")
            .withEnv("RESTATE_WORKER__STORAGE_ROCKSDB__PATH", "/state/worker")
            .withNetwork(network)
            .withNetworkAliases("runtime")
            .withLogConsumer(ContainerLogger(testReportDir, "restate-runtime"))

    // Mount state directory
    runtimeContainer!!.addFileSystemBind(
        stateDir.toString(), "/state", BindMode.READ_WRITE, SelinuxContext.SINGLE)

    if (System.getenv(IMAGE_PULL_POLICY_ENV) == ALWAYS_PULL) {
      runtimeContainer!!.withImagePullPolicy(PullPolicy.alwaysPull())
    }

    runtimeContainer!!.start()

    logger.debug("Restate runtime started and available at {}", getRuntimeGrpcIngressUrl())

    // Let's execute service discovery to register the services
    functionContainers.values.forEach { (spec, _) -> discoverServiceEndpoint(spec) }
  }

  fun discoverServiceEndpoint(spec: FunctionSpec) {
    val url = spec.getFunctionEndpointUrl()

    val body = Json.encodeToString(mapOf("uri" to url.toString()))

    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(URI.create("${getRuntimeMetaUrl()}endpoint/discover"))
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

  fun isRuntimeRunning(): Boolean {
    return runtimeContainer!!.currentContainerInfo!!.state!!.exitCodeLong == null
  }

  fun createRuntimeChannel(): ManagedChannel {
    return getRuntimeGrpcIngressUrl().let { url ->
      NettyChannelBuilder.forAddress(url.host, url.port).disableRetry().usePlaintext().build()
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
}
