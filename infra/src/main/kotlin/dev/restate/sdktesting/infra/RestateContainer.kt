// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.github.dockerjava.api.command.InspectContainerResponse
import dev.restate.sdktesting.infra.runtimeconfig.RestateConfigSchema
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.use
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.rnorth.ducttape.ratelimits.RateLimiterBuilder
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName

class RestateContainer(
    config: RestateDeployerConfig,
    val hostname: String,
    network: Network,
    envs: Map<String, String>,
    configSchema: RestateConfigSchema?,
    copyToContainer: List<Pair<String, Transferable>>,
    overrideContainerImage: String? = null,
    stateDirectoryMountOverride: String? = null,
    enableLocalPortForward: Boolean = true,
    private val enableHostNetwork: Boolean = false,
) :
    GenericContainer<RestateContainer>(
        DockerImageName.parse(overrideContainerImage ?: config.restateContainerImage)) {
  companion object {
    private val LOG = LogManager.getLogger(RestateContainer::class.java)
    private val TOML_MAPPER = ObjectMapper(TomlFactory())

    private val WAIT_STARTUP_STRATEGY =
        WaitAllStrategy()
            .withStrategy(
                Wait.forHttp("/restate/health")
                    .forPort(RUNTIME_INGRESS_ENDPOINT_PORT)
                    .withRateLimiter(
                        RateLimiterBuilder.newBuilder()
                            .withRate(200, TimeUnit.MILLISECONDS)
                            .withConstantThroughput()
                            .build()))
            .withStrategy(
                Wait.forHttp("/health")
                    .forPort(RUNTIME_ADMIN_ENDPOINT_PORT)
                    .withRateLimiter(
                        RateLimiterBuilder.newBuilder()
                            .withRate(200, TimeUnit.MILLISECONDS)
                            .withConstantThroughput()
                            .build()))
            .withStartupTimeout(120.seconds.toJavaDuration())

    /**
     * Wait strategy used when the container runs with `--network host`. The default
     * `Wait.forHttp(...)` strategy fails in that mode because there is no mapped port — the
     * container shares the host's network namespace, so we poll `127.0.0.1` directly on the
     * runtime's native ports.
     */
    private val HOST_NETWORK_WAIT_STARTUP_STRATEGY =
        object : AbstractWaitStrategy() {
              private val httpClient: HttpClient = HttpClient.newHttpClient()

              override fun waitUntilReady() {
                val timeoutSeconds = startupTimeout.seconds.toInt().coerceAtLeast(1)
                Unreliables.retryUntilTrue(timeoutSeconds, TimeUnit.SECONDS) {
                  probe("http://127.0.0.1:$RUNTIME_INGRESS_ENDPOINT_PORT/restate/health") &&
                      probe("http://127.0.0.1:$RUNTIME_ADMIN_ENDPOINT_PORT/health")
                }
              }

              private fun probe(url: String): Boolean {
                return try {
                  val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
                  val resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding())
                  resp.statusCode() in 200..299
                } catch (_: Exception) {
                  false
                }
              }
            }
            .withStartupTimeout(120.seconds.toJavaDuration())

    fun createRestateContainers(
        config: RestateDeployerConfig,
        network: Network,
        envs: Map<String, String>,
        configSchema: RestateConfigSchema?,
        copyToContainer: List<Pair<String, Transferable>>,
        overrideContainerImage: String?,
        overrideStateDirectoryMount: String?,
        nodes: Int,
        hostNetworkMode: Boolean = false,
    ): List<RestateContainer> {
      require(!hostNetworkMode || nodes == 1) {
        "Host networking is only supported with a single Restate node"
      }
      val clusterId = envs.get("RESTATE_CLUSTER_NAME") ?: UUID.randomUUID().toString()
      val replicationProperty = if (nodes == 1) 1 else 2
      // In host-network mode the bridge-network hostname `RESTATE_RUNTIME` doesn't resolve from
      // inside the container; use the host loopback instead. Single-node only so collisions on
      // RUNTIME_NODE_PORT can't happen between sibling Restate containers.
      val nodeHost = if (hostNetworkMode) "127.0.0.1" else RESTATE_RUNTIME
      val effectiveEnvs =
          envs +
              mapOf<String, String>(
                  "RESTATE_CLUSTER_NAME" to clusterId,
                  "RESTATE_AUTO_PROVISION" to "false",
                  "RESTATE_BIFROST__DEFAULT_PROVIDER" to "replicated",
                  "RESTATE_BIFROST__REPLICATED_LOGLET__DEFAULT_LOG_REPLICATION" to
                      replicationProperty.toString(),
                  "RESTATE_ROLES" to "[worker,http-ingress,log-server,admin,metadata-server]",
                  "RESTATE_METADATA_SERVER__TYPE" to "replicated",
                  "RESTATE_METADATA_CLIENT__ADDRESSES" to "[http://$nodeHost:$RUNTIME_NODE_PORT]",
              )

      return listOf(
          RestateContainer(
              config,
              // First node has the default hostname as usual, this makes sure containers
              // port injection annotations still work.
              RESTATE_RUNTIME,
              network,
              effectiveEnvs +
                  mapOf(
                      "RESTATE_AUTO_PROVISION" to "true",
                      "RESTATE_ADVERTISED_ADDRESS" to "http://$nodeHost:$RUNTIME_NODE_PORT"),
              configSchema,
              copyToContainer,
              overrideContainerImage,
              overrideStateDirectoryMount,
              enableHostNetwork = hostNetworkMode)) +
          (1.rangeUntil(nodes)).map {
            RestateContainer(
                config,
                "$RESTATE_RUNTIME-$it",
                network,
                effectiveEnvs +
                    mapOf(
                        "RESTATE_ADVERTISED_ADDRESS" to
                            "http://$RESTATE_RUNTIME-$it:$RUNTIME_NODE_PORT"),
                configSchema,
                copyToContainer,
                overrideContainerImage,
                overrideStateDirectoryMount,
                // Only the leader gets the privilege of local port forwarding
                enableLocalPortForward = false)
          }
    }
  }

  init {
    CloseableThreadContext.put("containerHostname", hostname).use {
      withImagePullPolicy(config.imagePullPolicy.toTestContainersImagePullPolicy())

      withEnv("RESTATE_NETWORKING__HTTP2_KEEP_ALIVE_INTERVAL", "4s")
      withEnv("RESTATE_NETWORKING__HTTP2_KEEP_ALIVE_TIMEOUT", "20s")
      withEnv(envs)
      // These envs should not be overriden by envs
      withEnv("RESTATE_ADMIN__BIND_ADDRESS", "0.0.0.0:$RUNTIME_ADMIN_ENDPOINT_PORT")
      withEnv("RESTATE_INGRESS__BIND_ADDRESS", "0.0.0.0:$RUNTIME_INGRESS_ENDPOINT_PORT")
      withEnv("DO_NOT_TRACK", "true")

      if (enableHostNetwork) {
        // Share the host's network namespace — the container reaches the in-process SDK at
        // 127.0.0.1:<port> directly, bypassing the Testcontainers SSH host-port relay. Network
        // aliases / withHostName are meaningless without a bridge network.
        withCreateContainerCmdModifier { cmd ->
          val existing =
              cmd.hostConfig ?: com.github.dockerjava.api.model.HostConfig.newHostConfig()
          cmd.withHostConfig(existing.withNetworkMode("host"))
        }
      } else {
        this.network = network
        this.networkAliases = arrayListOf(hostname)
        withCreateContainerCmdModifier { it.withHostName(hostname) }
      }

      withStartupAttempts(3) // For podman
      waitingFor(
          if (enableHostNetwork) HOST_NETWORK_WAIT_STARTUP_STRATEGY else WAIT_STARTUP_STRATEGY)

      val stateDirectoryMount = stateDirectoryMountOverride ?: config.stateDirectoryMount
      if (stateDirectoryMount != null) {
        val stateDir = File(stateDirectoryMount)
        stateDir.mkdirs()

        LOG.debug("Mounting state directory to '{}'", stateDir.toPath())
        addFileSystemBind(stateDir.toString(), "/state", BindMode.READ_WRITE, SelinuxContext.SHARED)
      }
      withEnv("RESTATE_BASE_DIR", "/state")

      if (configSchema != null) {
        withCopyToContainer(
            Transferable.of(TOML_MAPPER.writeValueAsBytes(configSchema)), "/config.toml")
        withEnv("RESTATE_CONFIG", "/config.toml")
      }

      for (file in copyToContainer) {
        withCopyToContainer(file.second, file.first)
      }

      // Port publication is a no-op with `--network host` (Docker ignores -p), and calling
      // addExposedPort there would mislead getMappedPort(...) into throwing later. With host
      // networking the container ports are reachable directly on the host loopback.
      if (!enableHostNetwork) {
        if (enableLocalPortForward && config.localAdminPort != null) {
          LOG.info("Going to expose Admin port on 'localhost:{}'", config.localAdminPort)
          super.addFixedExposedPort(config.localAdminPort, RUNTIME_ADMIN_ENDPOINT_PORT)
        } else {
          addExposedPort(RUNTIME_ADMIN_ENDPOINT_PORT)
        }
        if (enableLocalPortForward && config.localIngressPort != null) {
          LOG.info("Going to expose Admin port on 'localhost:{}'", config.localIngressPort)
          super.addFixedExposedPort(config.localIngressPort, RUNTIME_INGRESS_ENDPOINT_PORT)
        } else {
          addExposedPort(RUNTIME_INGRESS_ENDPOINT_PORT)
        }
        if (enableLocalPortForward && config.localNodePort != null) {
          LOG.info("Going to expose node port on 'localhost:{}'", config.localNodePort)
          super.addFixedExposedPort(config.localNodePort, RUNTIME_NODE_PORT)
        } else {
          addExposedPort(RUNTIME_NODE_PORT)
        }
      }
    }
  }

  fun configureLogger(testReportDir: String): RestateContainer {
    this.withLogConsumer(ContainerLogger(testReportDir, hostname))
    return this
  }

  fun waitStartup() {
    WAIT_STARTUP_STRATEGY.waitUntilReady(this)
  }

  fun dumpConfiguration() {
    check(isRunning) { "The container is not running, can't dump configuration" }
    dockerClient.killContainerCmd(containerId).withSignal("SIGUSR1").exec()
  }

  override fun getContainerInfo(): InspectContainerResponse {
    // We override container info to avoid getting outdated info when restarted
    return currentContainerInfo
  }
}
