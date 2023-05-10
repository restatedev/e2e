package dev.restate.e2e.utils

import com.github.dockerjava.api.command.InspectContainerResponse
import eu.rekawek.toxiproxy.ToxiproxyClient
import org.testcontainers.containers.Network
import org.testcontainers.containers.ToxiproxyContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget

internal class ProxyContainer(private val container: ToxiproxyContainer) {

  companion object {
    // From https://www.testcontainers.org/modules/toxiproxy/
    private const val PORT_OFFSET = 8666
    private const val MAX_PORTS = 31
  }

  private val client by lazy { ToxiproxyClient(container.host, container.controlPort) }

  private var assignedPorts = 0
  private val portMappings = mutableMapOf<String, Int>()

  // -- Lifecycle

  internal fun start(network: Network, testReportDir: String) {
    container
        .withNetwork(network)
        .withLogConsumer(ContainerLogger(testReportDir, "toxiproxy"))
        .start()
  }

  internal fun stop() {
    container.stop()
  }

  // -- Port mapping

  internal fun mapPort(hostName: String, port: Int): Int {
    assert(assignedPorts != MAX_PORTS) { "Cannot assign more ports" }

    val portName = "${hostName}-${port}"
    val upstreamAddress = address(hostName, port)
    val chosenPort = PORT_OFFSET + assignedPorts
    assignedPorts++

    client.createProxy(portName, address("0.0.0.0", chosenPort), upstreamAddress)
    portMappings[upstreamAddress] = chosenPort

    return chosenPort
  }

  internal fun getMappedPort(hostName: String, port: Int): Int? {
    return portMappings[address(hostName, port)]?.let { container.getMappedPort(it) }
  }

  internal fun waitPorts(hostName: String, vararg ports: Int) {
    val mappedPorts =
        ports.map {
          portMappings[address(hostName, it)]
              ?: throw IllegalArgumentException("Cannot wait on non existing port")
        }
    Wait.forListeningPort().waitUntilReady(WaitOnSpecificPortsTarget(mappedPorts, container))
  }

  private fun address(hostName: String, port: Int): String {
    return "${hostName}:${port}"
  }

  private class WaitOnSpecificPortsTarget(
      val ports: List<Int>,
      val proxyContainer: ToxiproxyContainer
  ) : WaitStrategyTarget {
    override fun getExposedPorts(): MutableList<Int> {
      return ports.toMutableList()
    }

    override fun getContainerInfo(): InspectContainerResponse {
      return proxyContainer.containerInfo
    }
  }
}
