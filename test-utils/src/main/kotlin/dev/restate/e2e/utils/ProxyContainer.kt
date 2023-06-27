package dev.restate.e2e.utils

import eu.rekawek.toxiproxy.ToxiproxyClient
import java.util.stream.IntStream
import org.testcontainers.containers.Network
import org.testcontainers.containers.ToxiproxyContainer
import org.testcontainers.containers.wait.strategy.Wait

internal class ProxyContainer(private val container: ToxiproxyContainer) {

  companion object {
    // From https://www.testcontainers.org/modules/toxiproxy/
    private const val TOXIPROXY_CONTROL_PORT = 8474
    // From https://www.testcontainers.org/modules/toxiproxy/
    private const val PORT_OFFSET = 8666
    // For the time being, we use only 2 ports.
    private const val MAX_PORTS = 2
  }

  private val client by lazy { ToxiproxyClient(container.host, container.controlPort) }

  private var assignedPorts = 0
  private val portMappings = mutableMapOf<String, Int>()

  // -- Lifecycle

  internal fun start(network: Network, testReportDir: String) {
    // We override what ToxiproxyContainer sets as default
    val portsToExpose =
        IntStream.concat(
                IntStream.of(TOXIPROXY_CONTROL_PORT),
                IntStream.range(PORT_OFFSET, PORT_OFFSET + MAX_PORTS))
            .boxed()
            .toArray { arrayOfNulls<Int>(it) }

    container
        .withNetwork(network)
        .withLogConsumer(ContainerLogger(testReportDir, "toxiproxy"))
        .withExposedPorts(*portsToExpose)
        .withStartupAttempts(3)
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
}
