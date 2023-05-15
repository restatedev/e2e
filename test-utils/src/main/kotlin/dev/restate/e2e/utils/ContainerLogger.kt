package dev.restate.e2e.utils

import com.github.dockerjava.api.DockerClient
import dev.restate.e2e.utils.ContainerLogger.Companion.collectAllNow
import org.testcontainers.DockerClientFactory
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.FrameConsumerResultCallback
import org.testcontainers.containers.output.OutputFrame
import java.time.Instant

/** Logger to dump to specific files the stdout and stderr of the containers */
internal class ContainerLogger(
    private val testReportDirectory: String,
    private val loggerName: String,
  private   val logCount: Int = 0
) : Consumer<OutputFrame> {

  companion object {
    internal fun ContainerLogger.collectAllNow(genericContainer: GenericContainer<*>) {
      attachConsumer(genericContainer.dockerClient, genericContainer.containerId, this).use {
        it.awaitCompletion()
        it.close()
      }
    }

    internal fun ContainerLogger.reFollow(genericContainer: GenericContainer<*>, since: Instant): ContainerLogger {
      val newLogger = ContainerLogger(this.testReportDirectory, this.loggerName, this.logCount + 1)
      attachConsumer(genericContainer.dockerClient, genericContainer.containerId, newLogger,
        since.toEpochMilli().toInt()
      )
      return newLogger
    }

    private fun attachConsumer(
        dockerClient: DockerClient,
        containerId: String,
        consumer: Consumer<OutputFrame>,
        since: Int = 0
    ): FrameConsumerResultCallback {
      val cmd =
          dockerClient.logContainerCmd(containerId)
            .withFollowStream(true)
            .withSince(since)
            .withStdOut(true)
            .withStdErr(true)
      val callback = FrameConsumerResultCallback()
      callback.addConsumer(OutputFrame.OutputType.STDOUT, consumer)
      callback.addConsumer(OutputFrame.OutputType.STDERR, consumer)
      return cmd.exec(callback)
    }
  }

  private var stdoutStream: BufferedWriter? = null
  private var stderrStream: BufferedWriter? = null

  override fun accept(frame: OutputFrame) {
    when (frame.type) {
      OutputFrame.OutputType.STDOUT -> {
        resolveStdoutStream().write(frame.utf8String)
      }
      OutputFrame.OutputType.STDERR -> {
        resolveStderrStream().write(frame.utf8String)
      }
      else -> {
        stdoutStream?.close()
        stderrStream?.close()
      }
    }
  }

  private fun resolveStdoutStream(): BufferedWriter {
    if (stdoutStream == null) {
      stdoutStream = newStream("stdout")
    }
    return stdoutStream!!
  }

  private fun resolveStderrStream(): BufferedWriter {
    if (stderrStream == null) {
      stderrStream = newStream("stderr")
    }
    return stderrStream!!
  }

  private fun newStream(
      type: String
  ): BufferedWriter {
    val path = Path.of(testReportDirectory, "${loggerName}_${logCount}_${type}.log")
    val fileExists = Files.exists(path)

    val writer =
        Files.newBufferedWriter(
            path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)

    if (fileExists) {
      writer.newLine()
      writer.newLine()
    }
    writer.write("========================= START LOG =========================\n\n")

    return writer
  }
}
