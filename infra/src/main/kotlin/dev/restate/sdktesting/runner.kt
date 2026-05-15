// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import com.charleskorn.kaml.encodeToStream
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.terminal.Terminal
import dev.restate.sdktesting.infra.ContainerServiceDeploymentConfig
import dev.restate.sdktesting.infra.LocalForwardServiceDeploymentConfig
import dev.restate.sdktesting.infra.PullPolicy
import dev.restate.sdktesting.infra.RestateDeployerConfig
import dev.restate.sdktesting.infra.ServiceSpec
import dev.restate.sdktesting.infra.registerGlobalConfig
import dev.restate.sdktesting.junit.ExecutionResult
import dev.restate.sdktesting.junit.SuiteProvider
import io.github.cdimascio.dotenv.Dotenv
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlinx.serialization.Serializable
import org.junit.platform.engine.Filter
import org.junit.platform.engine.discovery.ClassNameFilter
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.MethodFilter

@Serializable data class ExclusionsFile(val exclusions: Map<String, List<String>>? = emptyMap())

private class RestateE2ETests : CliktCommand() {
  override fun run() {
    // Disable log4j2 JMX, this prevents reconfiguration
    System.setProperty("log4j2.disable.jmx", "true")
    // This is hours of debugging, don't touch it
    // tl;dr this makes sure a single log4j2 configuration exists for the whole JVM,
    // important to make Configurator.reconfigure work
    System.setProperty(
        "log4j2.contextSelector", "org.apache.logging.log4j.core.selector.BasicContextSelector")
    // The default keep alive time is way too long, and this is a problem when we stop and restart
    // containers.
    System.setProperty("jdk.httpclient.keepalive.timeout", "5")
    // The health check strategy uses the HttpUrlConnection which has no connect timeout by default.
    // Could have caused the health check to hang indefinitely.
    System.setProperty("sun.net.client.defaultConnectTimeout", "5000")
  }
}

private class TestRunnerOptions : OptionGroup() {
  val restateContainerImage by
      option(envvar = "RESTATE_CONTAINER_IMAGE").help("Image used to run Restate")
  val reportDir by
      option(envvar = "TEST_REPORT_DIR").path().help("Base report directory").defaultLazy {
        defaultReportDirectory()
      }
  val imagePullPolicy by
      option()
          .enum<PullPolicy>()
          .help(
              "Pull policy for container images. ALWAYS skips pulling images prefixed with restate.local or localhost")
          .default(PullPolicy.ALWAYS)
  val customTestsFile by
      option("--custom-tests", "--custom-tests-file")
          .help("File containing the custom tests configurations")

  fun applyToDeployerConfig(deployerConfig: RestateDeployerConfig): RestateDeployerConfig {
    var newConfig = deployerConfig
    if (restateContainerImage != null) {
      newConfig = newConfig.copy(restateContainerImage = restateContainerImage!!)
    }
    if (customTestsFile != null) {
      newConfig = newConfig.copy(customTestsFile = customTestsFile!!)
    }
    newConfig = newConfig.copy(imagePullPolicy = imagePullPolicy)
    return newConfig
  }

  private fun defaultReportDirectory(): Path {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    return Path.of("test_report/${LocalDateTime.now().format(formatter)}").toAbsolutePath()
  }
}

private class FilterOptions(suites: SuiteProvider) : OptionGroup() {
  val testSuite by
      option()
          .required()
          .help(
              "Test suite to run. Available: ${listOf("all") + suites.allSuites().map { it.name }}")
  val testName by option().help("Name of the test class to run within the suite")
}

private abstract class TestRunCommand(help: String) : CliktCommand(help) {
  val testRunnerOptions by TestRunnerOptions()
}

private class Run(private val suites: SuiteProvider) :
    TestRunCommand("Run test suite, executing the service as a container.") {
  val filter by FilterOptions(suites).cooccurring()
  val exclusionsFile by
      option("--exclusions", "--exclusions-file", envvar = "TEST_EXCLUSIONS_FILE")
          .help("YAML file containing the excluded tests")
  val parallel by
      option()
          .flag("--sequential", default = true)
          .help(
              "Run tests in parallel (default). Use --sequential with Podman or to reduce resource usage.")
  val serviceContainerImage by
      option("--service-container-image", envvar = "SERVICE_CONTAINER_IMAGE")
          .help(
              "Docker image for the service under test. If omitted, no service container is deployed.")
  val serviceContainerEnvFile by
      option("--service-container-env-file")
          .help(".env file whose variables are injected into the service container")

  override fun run() {
    val terminal = Terminal()

    val additionalServiceEnvs =
        serviceContainerEnvFile
            ?.let { envFile ->
              Dotenv.configure().filename(envFile).load().entries().associate { it.key to it.value }
            }
            .orEmpty()

    val serviceDeploymentConfig =
        if (serviceContainerImage != null) {
          mapOf(
              ServiceSpec.DEFAULT_SERVICE_NAME to
                  ContainerServiceDeploymentConfig(serviceContainerImage!!, additionalServiceEnvs))
        } else {
          mapOf()
        }

    registerGlobalConfig(
        testRunnerOptions.applyToDeployerConfig(RestateDeployerConfig(serviceDeploymentConfig)))

    val testSuites = suites.resolveSuites(filter?.testSuite)

    val loadedExclusions: ExclusionsFile =
        if (exclusionsFile != null) {
          FileInputStream(File(exclusionsFile!!))
              .use { Yaml.default.decodeFromStream<ExclusionsFile>(it) }
              .also { exclusions ->
                println("Using exclusions file $exclusionsFile")
                println("Loaded exclusions: ${exclusions.exclusions}")
              }
        } else {
          ExclusionsFile()
        }

    val reports = mutableListOf<ExecutionResult>()
    val newExclusions = mutableMapOf<String, List<String>>()
    var newFailures = false
    for (testSuite in testSuites) {
      val exclusions = loadedExclusions.exclusions?.get(testSuite.name) ?: emptyList()
      val exclusionsFilters =
          if (exclusions.isNotEmpty()) listOf(MethodFilter.excludeMethodNamePatterns(exclusions))
          else listOf()
      val cliOptionFilter =
          filter?.testName?.let {
            listOf(ClassNameFilter.includeClassNamePatterns(testClassNameToFQCN(it)))
          } ?: emptyList<Filter<*>>()

      val report =
          testSuite.runTests(
              terminal,
              testRunnerOptions.reportDir,
              exclusionsFilters + cliOptionFilter,
              false,
              parallel)

      reports.add(report)
      report.printFailuresToFiles(testRunnerOptions.reportDir)

      val failures = report.failedTests
      if (failures.isNotEmpty() || exclusions.isNotEmpty()) {
        newExclusions[testSuite.name] =
            (failures
                    .mapNotNull { it.source.getOrNull() }
                    .mapNotNull {
                      when (it) {
                        is MethodSource -> "${it.className}.${it.methodName}"
                        else -> null
                      }
                    }
                    .distinct() + exclusions)
                .sorted()
      }
      if (failures.isNotEmpty()) {
        newFailures = true
      }
    }

    FileOutputStream(testRunnerOptions.reportDir.resolve("exclusions.new.yaml").toFile()).use {
      Yaml.default.encodeToStream(ExclusionsFile(newExclusions.toSortedMap()), it)
    }

    val succeededTests = reports.sumOf { it.succeededTests }
    val executedTests = reports.sumOf { it.executedTests }
    val testsStyle = if (succeededTests == executedTests) green else red
    val testsInfoLine = testsStyle("* Succeeded tests: $succeededTests / $executedTests")

    val failedClasses = reports.sumOf { it.executedClasses - it.succeededClasses }
    val classesStyle = if (failedClasses != 0) red else green
    val classesInfoLine = classesStyle("* Failed classes initialization: $failedClasses")

    val totalDuration = reports.fold(Duration.ZERO) { d, res -> d + res.executionDuration }

    println(
        """
            ${bold("========================= Final results =========================")}
            🗈 Report directory: ${testRunnerOptions.reportDir}
            * Run test suites: ${reports.map { it.testSuite }}
            $testsInfoLine
            $classesInfoLine
            * Execution time: $totalDuration
        """
            .trimIndent())

    for (report in reports) {
      report.printFailuresToTerminal(terminal)
    }

    exitProcess(if (newFailures) 1 else 0)
  }
}

private class Debug(private val suites: SuiteProvider) :
    TestRunCommand(
        "Run a single test without a service container, forwarding to a local process.") {
  val testSuite by
      option()
          .default(suites.defaultSuite.name)
          .help(
              "Test suite to use for environment setup. Available: ${suites.allSuites().map { it.name }}")
  val testName by option().required().help("Name of the test class to run")
  val localContainers by
      argument()
          .convert { spec ->
            if (spec.contains('=')) spec.split('=', limit = 2).let { it[0] to it[1].toInt() }
            else ServiceSpec.DEFAULT_SERVICE_NAME to spec.toInt()
          }
          .multiple(required = true)
          .help("Local service ports: '9080' or 'serviceName=9080'. Repeatable.")
  val retainAfterEnd by
      option()
          .flag("--dont-retain-after-end", default = false)
          .help("Keep the Docker network alive after the test ends (requires manual cleanup)")
  val mountStateDirectory by option().help("Mount a local directory as the Restate data directory")
  val localIngressPort by option().int().help("Bind Restate ingress to this host port")
  val localAdminPort by option().int().help("Bind Restate admin to this host port")
  val localNodePort by option().int().help("Bind Restate node-to-node port to this host port")

  override fun run() {
    val terminal = Terminal()

    val restateDeployerConfig =
        RestateDeployerConfig(
            localContainers.associate {
              it.first to LocalForwardServiceDeploymentConfig(it.second)
            },
            localAdminPort = this.localAdminPort,
            localIngressPort = this.localIngressPort,
            localNodePort = this.localNodePort,
            stateDirectoryMount = this.mountStateDirectory,
            retainAfterEnd = this.retainAfterEnd)
    registerGlobalConfig(testRunnerOptions.applyToDeployerConfig(restateDeployerConfig))

    val suite = suites.resolveSuites(testSuite)[0]
    val testFilters =
        listOf(ClassNameFilter.includeClassNamePatterns(testClassNameToFQCN(testName)))

    val report = suite.runTests(terminal, testRunnerOptions.reportDir, testFilters, true, false)

    report.printFailuresToTerminal(terminal)
    report.printFailuresToFiles(testRunnerOptions.reportDir)

    exitProcess(if (report.failedTests.isNotEmpty()) 1 else 0)
  }
}

fun runMain(args: Array<String>, suites: SuiteProvider) {
  val actualArgs = if (args.isEmpty()) arrayOf("run") else args
  RestateE2ETests().subcommands(Run(suites), Debug(suites)).main(actualArgs)
}

private fun testClassNameToFQCN(className: String): String {
  if (className.contains('.')) return className
  return "dev.restate.sdktesting.tests.$className"
}
