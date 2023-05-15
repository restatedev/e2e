package dev.restate.e2e.utils

import com.github.dockerjava.api.command.InspectContainerResponse
import dev.restate.e2e.utils.ContainerLogger.Companion.collectAllNow
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget

internal open class NotCachedContainerInfo(private val delegate: WaitStrategyTarget) :
    WaitStrategyTarget by delegate {
  override fun getContainerInfo(): InspectContainerResponse {
    // Need to ask the delegate to avoid recursive call to getContainerInfo()
    val containerId = this.delegate.containerId
    return dockerClient.inspectContainerCmd(containerId).exec()
  }
}

internal class WaitOnSpecificPortsTarget(
    private val ports: List<Int>,
    delegate: WaitStrategyTarget
) : NotCachedContainerInfo(delegate) {
  override fun getExposedPorts(): MutableList<Int> {
    return ports.toMutableList()
  }
}

internal fun GenericContainer<*>.writeLogsTo(testReportDirectory: String, loggerName: String) {
  ContainerLogger(testReportDirectory, loggerName).collectAllNow(this)
}
