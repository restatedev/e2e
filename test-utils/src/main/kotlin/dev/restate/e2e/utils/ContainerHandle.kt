package dev.restate.e2e.utils

import com.github.dockerjava.api.DockerClient
import dev.restate.e2e.utils.ContainerLogger.Companion.reFollow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.apache.logging.log4j.LogManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy
import org.testcontainers.utility.LogUtils
import java.time.Instant

/** Handle to interact with deployed containers */
class ContainerHandle
internal constructor(
    private val container: GenericContainer<*>,
    private val getMappedPort: (Int) -> Int? = { container.getMappedPort(it) },
    private val waitStrategy: () -> Unit = {},
) {

  private val logger = LogManager.getLogger(ContainerHandle::class.java)

  fun terminateAndRestart() {
    val now = Instant.now()
    logger.info(
        "Going to kill and restart the container {} with hostnames {}.",
        container.containerName,
        container.networkAliases.joinToString())
    retryDockerClientCommand { dockerClient, containerId ->
      dockerClient.restartContainerCmd(containerId).exec()
    }

    postStart(now)
  }

  fun killAndRestart() {
    val now = Instant.now()
    logger.info(
        "Going to kill and restart the container {} with hostnames {}.",
        container.containerName,
        container.networkAliases.joinToString())
    retryDockerClientCommand { dockerClient, containerId ->
      // Using timeout 0 because I'm missing a signal argument
      // https://github.com/docker-java/docker-java/issues/2123
      dockerClient.restartContainerCmd(containerId).withTimeout(0).exec()
    }

    postStart(now)
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
      val now = Instant.now()
      logger.info(
          "Going to start the container {} with hostnames {}.",
          container.containerName,
          container.networkAliases.joinToString())
      retryDockerClientCommand { dockerClient, containerId ->
        dockerClient.startContainerCmd(containerId).exec()
      }

      postStart(now)
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
    return this.getMappedPort.invoke(port)
  }

  private fun postStart(now: Instant) {
    // Wait for running start
    IsRunningStartupCheckStrategy()
        .waitUntilStartupSuccessful(container.dockerClient, container.containerId)

    // We need to start following again, as stopping also stops following logs
    container.logConsumers.forEach {
      if (it is ContainerLogger) {
        it.reFollow(container, now)
      }
    }

    // Additional wait strategy for ports
    waitStrategy()

    logger.info("Container {} started and passed all the checks.", container.containerName)
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
