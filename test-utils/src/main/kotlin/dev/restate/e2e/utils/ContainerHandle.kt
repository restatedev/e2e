package dev.restate.e2e.utils

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.LogUtils

/** Handle to interact with deployed containers */
class ContainerHandle internal constructor(private val container: GenericContainer<*>) {

  fun terminateAndRestart() {
    terminate()
    start()
  }

  fun killAndRestart() {
    kill()
    start()
  }

  fun terminate() {
    container.dockerClient.stopContainerCmd(container.containerId).exec()
  }

  fun kill() {
    container.dockerClient.killContainerCmd(container.containerId).exec()
  }

  fun start() {
    if (!isRunning()) {
      container.dockerClient.startContainerCmd(container.containerId).exec()

      // We need to start follow again, as stopping also stops following logs
      container.logConsumers.forEach {
        LogUtils.followOutput(container.dockerClient, container.containerId, it)
      }
    }
  }

  fun isRunning(): Boolean {
    return container.currentContainerInfo?.state?.running ?: false
  }

  fun getMappedPort(port: Int): Int? {
    return container.getMappedPort(port)
  }
}
