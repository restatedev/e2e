// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests.openapi

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.utility.MountableFile

/** Docker is already required by the suite; no separate local oasdiff installation is needed. */
fun checkCompatibility(baseline: Path, candidate: Path, report: Path) {
  // Testcontainers removes a one-shot container after a nonzero exit. Capture its output before
  // that cleanup, so a breaking-change failure still leaves a useful report.
  val output = StringBuffer()
  GenericContainer("tufin/oasdiff:v1.33.0")
      .apply {
        withLogConsumer { output.append(it.utf8String) }
        withCopyFileToContainer(MountableFile.forHostPath(baseline), "/spec/baseline.json")
        withCopyFileToContainer(MountableFile.forHostPath(candidate), "/spec/candidate.json")
        withCommand(
            "breaking",
            "--fail-on",
            "ERR",
            "--format",
            "text",
            "/spec/baseline.json",
            "/spec/candidate.json",
        )
        withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))
        withStartupAttempts(1)
      }
      .use { container ->
        try {
          container.start()
        } catch (e: Exception) {
          throw AssertionError("OpenAPI compatibility check failed; see $report", e)
        } finally {
          Files.writeString(
              report,
              if (container.containerId != null) container.logs else output.toString(),
          )
        }
      }
}
