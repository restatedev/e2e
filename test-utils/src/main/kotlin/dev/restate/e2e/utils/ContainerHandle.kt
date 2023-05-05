package dev.restate.e2e.utils

import com.github.dockerjava.api.DockerClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.apache.logging.log4j.LogManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.LogUtils

/** Handle to interact with deployed containers */
class ContainerHandle internal constructor(private val container: GenericContainer<*>) {

  private val logger = LogManager.getLogger(ContainerHandle::class.java)

  fun terminateAndRestart() {
    terminate()
    start()
  }

  fun killAndRestart() {
    kill()
    start()
  }

  fun terminate() {
    terminate(10.seconds)
  }

  fun terminate(timeout: Duration) {
    logger.info(
        "Going to terminate the container {} with hostnames {}.",
        container.containerName,
        container.networkAliases.joinToString())
    retryDockerClientCommand { dockerClient, containerId ->
      dockerClient.stopContainerCmd(containerId).withTimeout(timeout.inWholeSeconds.toInt()).exec()
    }
  }

  fun kill() {
    logger.info(
        "Going to kill the container {} with hostnames {}.",
        container.containerName,
        container.networkAliases.joinToString())
    retryDockerClientCommand { dockerClient, containerId ->
      dockerClient.killContainerCmd(containerId).exec()
    }
  }

  fun start() {
    if (!isRunning()) {
      logger.info(
          "Going to start the container {} with hostnames {}.",
          container.containerName,
          container.networkAliases.joinToString())
      retryDockerClientCommand { dockerClient, containerId ->
        dockerClient.startContainerCmd(containerId).exec()
      }

      // We need to start following again, as stopping also stops following logs
      container.logConsumers.forEach {
        LogUtils.followOutput(container.dockerClient, container.containerId, it)
      }
    }
  }

  fun isRunning(): Boolean {
    return retryDockerClientCommand { dockerClient, containerId ->
          dockerClient.inspectContainerCmd(containerId).exec()
        }
        .state.running
        ?: false
  }

  fun getMappedPort(port: Int): Int? {
    return container.getMappedPort(port)
  }

  private fun <T> retryDockerClientCommand(fn: (DockerClient, String) -> T): T {
    val client = container.dockerClient
    val containerId = container.containerId

    // In most of the cases, these retries are not necessary.
    // In Podman environments though, we might hit the case where we need it due to how
    // podman.socket works.
    var lastException: Throwable? = null
    for (i in 0..10) {
      try {
        return fn(client, containerId)
      } catch (exception: Throwable) {
        logger.warn(
            "Error when trying to execute docker command: {}. This might be a problem with the local docker daemon.",
            exception.message)
        lastException = exception
      }
    }
    throw lastException!!
  }
}
