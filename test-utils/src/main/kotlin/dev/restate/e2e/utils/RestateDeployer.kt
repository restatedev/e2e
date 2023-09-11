package dev.restate.e2e.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import dev.restate.e2e.utils.config.RestateConfigSchema
import dev.restate.e2e.utils.meta.client.EndpointsClient
import dev.restate.e2e.utils.meta.models.RegisterServiceEndpointRequest
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.File
import java.lang.reflect.Method
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import org.testcontainers.shaded.com.github.dockerjava.core.DockerClientConfig
import org.testcontainers.utility.DockerImageName

class RestateDeployer
private constructor(
    runtimeDeployments: Int,
    serviceSpecs: List<ServiceSpec>,
    private val additionalContainers: Map<String, GenericContainer<*>>,
    private val additionalEnv: Map<String, String>,
    private val runtimeContainerName: String,
    private val enableTracesExport: Boolean,
    private val configSchema: RestateConfigSchema?
) : AutoCloseable, ExtensionContext.Store.CloseableResource {

  // Perhaps at some point we could autogenerate these from the openapi doc and also remove the need
  // to manually implement these serialization routines
  sealed class RetryPolicy {

    abstract fun toInvokerSetupEnv(): Map<String, String>

    object None : RetryPolicy() {
      override fun toInvokerSetupEnv(): Map<String, String> {
        return mapOf("RESTATE_WORKER__INVOKER__RETRY_POLICY__TYPE" to "None")
      }
    }

    class FixedDelay(private val interval: String, private val maxAttempts: Int) : RetryPolicy() {
      override fun toInvokerSetupEnv(): Map<String, String> {
        return mapOf(
            "RESTATE_WORKER__INVOKER__RETRY_POLICY__TYPE" to "FixedDelay",
            "RESTATE_WORKER__INVOKER__RETRY_POLICY__INTERVAL" to interval,
            "RESTATE_WORKER__INVOKER__RETRY_POLICY__MAX_ATTEMPTS" to maxAttempts.toString())
      }
    }
  }

  companion object {
    private const val RESTATE_RUNTIME_CONTAINER_ENV = "RESTATE_RUNTIME_CONTAINER"
    private const val DEFAULT_RUNTIME_CONTAINER = "ghcr.io/restatedev/restate"

    private const val CONTAINER_LOGS_DIR_ENV = "CONTAINER_LOGS_DIR"

    private const val IMAGE_PULL_POLICY_ENV = "E2E_IMAGE_PULL_POLICY"
    private const val ALWAYS_PULL = "always"

    private const val MOUNT_STATE_DIRECTORY_ENV = "E2E_MOUNT_STATE_DIRECTORY"

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

  private val serviceContainers =
      serviceSpecs.associate { spec -> spec.hostName to (spec to spec.toContainer()) }
  private val network = Network.newNetwork()

  private val proxyContainer = ProxyContainer(ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0"))
  private val runtimeContainer = GenericContainer(DockerImageName.parse(runtimeContainerName))
  private val deployedContainers: Map<String, ContainerHandle> =
      mapOf(
          RESTATE_RUNTIME to
              ContainerHandle(
                  runtimeContainer,
                  { proxyContainer.getMappedPort(RESTATE_RUNTIME, it) },
                  {
                    Wait.forListeningPort().waitUntilReady(NotCachedContainerInfo(runtimeContainer))
                    waitRuntimeHealthy()
                  })) +
          serviceContainers.map { it.key to ContainerHandle(it.value.second) } +
          additionalContainers.map { it.key to ContainerHandle(it.value) }

  init {
    assert(runtimeDeployments == 1) { "At the moment only one runtime deployment is supported" }
  }

  data class Builder(
      private var runtimeDeployments: Int = 1,
      private var serviceEndpoints: MutableList<ServiceSpec> = mutableListOf(),
      private var additionalContainers: MutableMap<String, GenericContainer<*>> = mutableMapOf(),
      private var additionalEnv: MutableMap<String, String> = mutableMapOf(),
      private var runtimeContainer: String =
          System.getenv(RESTATE_RUNTIME_CONTAINER_ENV) ?: DEFAULT_RUNTIME_CONTAINER,
      private var invokerRetryPolicy: RetryPolicy? = null,
      private var enableTracesExport: Boolean = true,
      private var configSchema: RestateConfigSchema? = null
  ) {

    fun withServiceEndpoint(serviceSpec: ServiceSpec) = apply {
      this.serviceEndpoints.add(serviceSpec)
    }

    fun withServiceEndpoint(serviceSpecBuilder: ServiceSpec.Builder) = apply {
      this.serviceEndpoints.add(serviceSpecBuilder.build())
    }

    /** Add a service with default configuration. */
    fun withServiceEndpoint(containerImageName: String) = apply {
      this.withServiceEndpoint(ServiceSpec.builder(containerImageName))
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

    fun withInvokerRetryPolicy(policy: RetryPolicy) = apply { this.invokerRetryPolicy = policy }

    fun disableTracesExport() = apply { this.enableTracesExport = false }

    fun withConfig(configSchema: RestateConfigSchema) = apply { this.configSchema = configSchema }

    fun build() =
        RestateDeployer(
            runtimeDeployments,
            serviceEndpoints,
            additionalContainers,
            additionalEnv + (invokerRetryPolicy?.toInvokerSetupEnv() ?: emptyMap()),
            runtimeContainer,
            enableTracesExport,
            configSchema)
  }

  fun deployAll(testReportDir: String) {
    deployServices(testReportDir)
    deployAdditionalContainers(testReportDir)
    deployRuntime(testReportDir)
    deployProxy(testReportDir)

    waitRuntimeHealthy()

    // Let's execute service discovery to register the services
    val client =
        EndpointsClient(
            ObjectMapper(),
            "http://localhost:${getContainerPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT)}",
            OkHttpClient())
    serviceContainers.values.forEach { (spec, _) -> discoverServiceEndpoint(client, spec) }

    // Log environment
    writeEnvironmentReport(testReportDir)
  }

  private fun deployServices(testReportDir: String) {
    // Deploy services
    serviceContainers.forEach { (serviceName, serviceContainer) ->
      serviceContainer.second.networkAliases = ArrayList()
      serviceContainer.second
          .withNetwork(network)
          .withNetworkAliases(serviceName)
          .withLogConsumer(ContainerLogger(testReportDir, serviceName))
          .start()
      logger.debug(
          "Started service container {} with endpoint {}",
          serviceName,
          serviceContainer.first.getEndpointUrl())
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

    logger.debug("Starting runtime container '{}'", runtimeContainerName)

    // Configure runtime container
    runtimeContainer
        // We expose these ports only to enable port checks
        .withExposedPorts(RUNTIME_GRPC_INGRESS_ENDPOINT_PORT, RUNTIME_META_ENDPOINT_PORT)
        .dependsOn(serviceContainers.values.map { it.second })
        .dependsOn(additionalContainers.values)
        .withEnv(additionalEnv)
        // These envs should not be overriden by additionalEnv
        .withEnv("RESTATE_META__REST_ADDRESS", "0.0.0.0:${RUNTIME_META_ENDPOINT_PORT}")
        .withEnv(
            "RESTATE_WORKER__INGRESS_GRPC__BIND_ADDRESS",
            "0.0.0.0:${RUNTIME_GRPC_INGRESS_ENDPOINT_PORT}")
        .withNetwork(network)
        .withNetworkAliases(RESTATE_RUNTIME)
        .withLogConsumer(ContainerLogger(testReportDir, "restate-runtime"))

    if ("true" == System.getenv(MOUNT_STATE_DIRECTORY_ENV)) {
      val stateDir = File(testReportDir, "state")
      stateDir.mkdirs()

      logger.debug("Mounting state directory to '{}'", stateDir.toPath())
      runtimeContainer.addFileSystemBind(
          stateDir.toString(), "/state", BindMode.READ_WRITE, SelinuxContext.SINGLE)
    }
    runtimeContainer
        .withEnv("RESTATE_META__STORAGE_PATH", "/state/meta")
        .withEnv("RESTATE_WORKER__STORAGE_ROCKSDB__PATH", "/state/worker")

    if (this.enableTracesExport) {
      // Create and mount traces directory
      val tracesDir = File(testReportDir, "traces")
      tracesDir.mkdirs()
      runtimeContainer.addFileSystemBind(
          tracesDir.toString(), "/traces", BindMode.READ_WRITE, SelinuxContext.SINGLE)
      runtimeContainer.withEnv("RESTATE_OBSERVABILITY__TRACING__JSON_FILE_EXPORT_PATH", "/traces")
    }

    if (this.configSchema != null) {
      val yamlMapper =
          ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
      runtimeContainer.withCopyToContainer(
          Transferable.of(yamlMapper.writeValueAsBytes(this.configSchema)), "/config.yaml")
      runtimeContainer.withEnv("RESTATE_CONFIG", "/config.yaml")
    }

    if (System.getenv(IMAGE_PULL_POLICY_ENV) == ALWAYS_PULL) {
      runtimeContainer.withImagePullPolicy(PullPolicy.alwaysPull())
    }

    runtimeContainer.start()
    logger.debug("Restate runtime started. Container id {}", runtimeContainer.containerId)
  }

  private fun deployProxy(testReportDir: String) {
    // We use an external proxy to access from the test code to the restate container in order to
    // retain the tcp port binding across restarts.

    // Configure toxiproxy and start
    proxyContainer.start(network, testReportDir)

    // Proxy runtime ports
    proxyContainer.mapPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT)
    proxyContainer.mapPort(RESTATE_RUNTIME, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT)

    logger.debug(
        "Toxiproxy started. gRPC ingress port: {}. Meta REST API port: {}",
        proxyContainer.getMappedPort(RESTATE_RUNTIME, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT),
        proxyContainer.getMappedPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT))
  }

  private fun waitRuntimeHealthy() {
    proxyContainer.waitHttp(
        Wait.forHttp("/health"),
        RESTATE_RUNTIME,
        RUNTIME_META_ENDPOINT_PORT,
    )
    proxyContainer.waitHttp(
        Wait.forHttp("/grpc.health.v1.Health/Check"),
        RESTATE_RUNTIME,
        RUNTIME_GRPC_INGRESS_ENDPOINT_PORT,
    )
    logger.debug("Runtime META and Ingress healthy")
  }

  fun discoverServiceEndpoint(client: EndpointsClient, spec: ServiceSpec) {
    val url = spec.getEndpointUrl()
    if (spec.skipRegistration) {
      logger.debug("Skipping registration for endpoint {}", url)
      return
    }

    val request =
        RegisterServiceEndpointRequest(
            uri = url.toString(),
            additionalHeaders = spec.registrationOptions.additionalHeaders,
            force = false)

    val response = client.createServiceEndpoint(request)

    if (response.statusCode !in 200..299) {
      fail(
          "Error when discovering endpoint $url, " +
              "got status code ${response.statusCode} with body: ${response.data?.toPrettyString()}")
    }

    logger.debug("Successfully executed discovery for endpoint {}. Result: {}", url, response.data)
  }

  private fun writeEnvironmentReport(testReportDir: String) {
    val outFile = File(testReportDir, "deployer_environment.json")

    // A bit of Jackson magic to get the object mapper well configured from docker client.
    // We also need to exclude rawValues otherwise we get all the entries serialized twice.
    DockerClientConfig.getDefaultObjectMapper()
        .writer(
            SimpleFilterProvider()
                .addFilter("rawValues", SimpleBeanPropertyFilter.serializeAllExcept("rawValues")))
        .withDefaultPrettyPrinter()
        .writeValue(
            outFile,
            deployedContainers.map { it.key to it.value.container.containerInfo.rawValues }.toMap())
  }

  private fun teardownAdditionalContainers() {
    additionalContainers.forEach { (_, container) -> container.stop() }
  }

  private fun teardownServices() {
    serviceContainers.forEach { (_, container) -> container.second.stop() }
  }

  private fun teardownRuntime() {
    // The reason to terminate it with the container handle is to try to perform a graceful
    // shutdown,
    // to let flush the logs and spans exported as files.
    // We keep a short timeout though as we don't want to influence too much the teardown time of
    // the tests.
    getContainerHandle(RESTATE_RUNTIME).terminate(1.seconds)
    runtimeContainer.stop()
  }

  private fun teardownProxy() {
    proxyContainer.stop()
  }

  private fun teardownAll() {
    teardownRuntime()
    teardownAdditionalContainers()
    teardownServices()
    teardownProxy()
    network!!.close()
  }

  internal fun createRuntimeChannel(): ManagedChannel {
    return getContainerPort(RESTATE_RUNTIME, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT).let { port ->
      NettyChannelBuilder.forAddress("127.0.0.1", port)
          .disableServiceConfigLookUp()
          .usePlaintext()
          .build()
    }
  }

  internal fun getContainerPort(hostName: String, port: Int): Int {
    return deployedContainers[hostName]?.getMappedPort(port)
        ?: throw java.lang.IllegalStateException(
            "Requested port for container $hostName, but the container or the port was not found")
  }

  fun getContainerHandle(hostName: String): ContainerHandle {
    return deployedContainers[hostName]
        ?: throw java.lang.IllegalArgumentException("Cannot find container $hostName")
  }

  override fun close() {
    teardownAll()
  }
}
