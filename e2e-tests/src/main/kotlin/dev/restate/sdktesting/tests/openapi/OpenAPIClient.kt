// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests.openapi

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import org.openapitools.codegen.DefaultGenerator
import org.openapitools.codegen.config.CodegenConfigurator

/** Validate and compile the document actually served by the candidate, not the baseline client. */
fun generateClient(
    spec: Path,
    reportDir: Path,
    packageName: String,
    vararg smokeSources: String,
): URLClassLoader {
  // A reused --report-dir must never let stale generated files satisfy compilation.
  val output = Files.createTempDirectory(Files.createDirectories(reportDir), "generated-")
  val configurator =
      CodegenConfigurator().apply {
        setInputSpec(spec.toString())
        setOutputDir(output.toString())
        setGeneratorName("java")
        setLibrary("native")
        setInvokerPackage("$packageName.client")
        setApiPackage("$packageName.api")
        setModelPackage("$packageName.model")
        setValidateSpec(true)
        setGlobalProperties(
            mapOf(
                "apiTests" to "false",
                "modelTests" to "false",
                "apiDocs" to "false",
                "modelDocs" to "false",
            )
        )
        setAdditionalProperties(
            mapOf("openApiNullable" to "false", "hideGenerationTimestamp" to "true")
        )
      }
  val files = DefaultGenerator().opts(configurator.toClientOptInput()).generate()
  check(files.isNotEmpty()) { "No client generated for $spec" }

  val sourceDir = output.resolve("src/main/java")
  for (smokeSource in smokeSources) {
    val resource = checkNotNull(object {}.javaClass.getResourceAsStream("/openapi/$smokeSource"))
    resource.use { Files.copy(it, sourceDir.resolve(smokeSource)) }
  }
  val sources =
      Files.walk(sourceDir).use { paths ->
        paths.filter { it.toString().endsWith(".java") }.map { it.toFile() }.toList()
      }
  check(sources.isNotEmpty()) { "No Java sources generated for $spec" }
  val classes = Files.createDirectories(output.resolve("classes"))
  val compiler =
      checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Run OpenAPI tests with a JDK" }
  Files.newBufferedWriter(output.resolve("javac.log")).use { log ->
    compiler.getStandardFileManager(null, null, null).use { manager ->
      val success =
          compiler
              .getTask(
                  log,
                  manager,
                  null,
                  listOf(
                      "-classpath",
                      System.getProperty("java.class.path"),
                      "-d",
                      classes.toString(),
                  ),
                  null,
                  manager.getJavaFileObjectsFromFiles(sources),
              )
              .call()
      check(success) { "Generated client does not compile: ${output.resolve("javac.log")}" }
    }
  }
  return URLClassLoader(arrayOf(classes.toUri().toURL()), object {}.javaClass.classLoader)
}
