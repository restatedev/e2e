package dev.restate.e2e.utils

import com.github.dockerjava.api.DockerClient
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.FrameConsumerResultCallback
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.output.WaitingConsumer

/** Logger to dump to specific files the stdout and stderr of the containers */
internal class ContainerLogger(
    private val testReportDirectory: String,
    private val loggerName: String
) : Consumer<OutputFrame> {

  companion object {
    internal fun ContainerLogger.collectAllNow(genericContainer: GenericContainer<*>) {
      val wait = WaitingConsumer()
      getLogs(genericContainer.dockerClient, genericContainer.containerId, this.andThen(wait)).use {
        wait.waitUntilEnd()
        it.completionLatch.await()
      }
      this.accept(OutputFrame.END)
    }

    private fun getLogs(
        dockerClient: DockerClient,
        containerId: String,
        consumer: Consumer<OutputFrame>
    ): FrameConsumerResultCallback {
      val cmd =
          dockerClient.logContainerCmd(containerId).withSince(0).withStdOut(true).withStdErr(true)
      val callback = FrameConsumerResultCallback()
      callback.addConsumer(OutputFrame.OutputType.STDOUT, consumer)
      callback.addConsumer(OutputFrame.OutputType.STDERR, consumer)
      return cmd.exec(callback)
    }
  }

  private var logCount = 0
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
        stdoutStream = null
        stderrStream = null
        logCount++
      }
    }
  }

  private fun resolveStdoutStream(): BufferedWriter {
    if (stdoutStream == null) {
      stdoutStream = newStream(testReportDirectory, loggerName, "stdout")
    }
    return stdoutStream!!
  }

  private fun resolveStderrStream(): BufferedWriter {
    if (stderrStream == null) {
      stderrStream = newStream(testReportDirectory, loggerName, "stderr")
    }
    return stderrStream!!
  }

  private fun newStream(
      testReportDirectory: String,
      loggerName: String,
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
    writer.write("========================= START LOG =========================\n")

    return writer
  }
}
