package dev.restate.e2e.utils

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import org.testcontainers.containers.output.OutputFrame

/** Logger to dump to specific files the stdout and stderr of the containers */
internal class ContainerLogger(testReportDirectory: String, loggerName: String) :
    Consumer<OutputFrame> {

  private val stdoutStream = newStream(testReportDirectory, loggerName, "stdout")
  private val stderrStream = newStream(testReportDirectory, loggerName, "stderr")

  override fun accept(frame: OutputFrame) {
    when (frame.type) {
      OutputFrame.OutputType.STDOUT -> {
        stdoutStream.write(frame.utf8String)
      }
      OutputFrame.OutputType.STDERR -> {
        stderrStream.write(frame.utf8String)
      }
      else -> {
        stdoutStream.close()
        stderrStream.close()
      }
    }
  }

  private fun newStream(
      testReportDirectory: String,
      loggerName: String,
      type: String
  ): BufferedWriter {
    val path = Path.of(testReportDirectory, "${loggerName}_${type}.log")
    val fileExists = Files.exists(path)

    val writer =
        Files.newBufferedWriter(
            Path.of(testReportDirectory, "${loggerName}_${type}.log"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND)

    if (fileExists) {
      writer.newLine()
      writer.newLine()
    }

    writer.write("========================= START LOG =========================\n")

    return writer
  }
}
