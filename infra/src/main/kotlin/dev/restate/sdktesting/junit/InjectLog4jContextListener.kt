// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.junit

import kotlin.jvm.optionals.getOrNull
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.ThreadContext
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

class InjectLog4jContextListener(
    val suiteName: String,
    private val parallel: Boolean,
) : TestExecutionListener {

  companion object {
    const val TEST_CLASS = "test_class"
    private val LOG = LogManager.getLogger(InjectLog4jContextListener::class.java)
  }

  @Volatile var testPlan: TestPlan? = null

  override fun testPlanExecutionStarted(testPlan: TestPlan) {
    this.testPlan = testPlan
    if (parallel) {
      LOG.warn(
          "Suite '{}' runs test classes in PARALLEL: testrunner.log lines from different tests " +
              "interleave. Correlate using the per-test routed logs (<reportDir>/<TestClass>/testRunner.log), " +
              "or rerun with --sequential for clean boundaries.",
          suiteName)
    }
  }

  override fun executionStarted(testIdentifier: TestIdentifier) {
    when (val source = testIdentifier.source.getOrNull()) {
      is ClassSource ->
          if (testIdentifier.isContainer) {
            val testClass = classSimpleName(source.className)
            ThreadContext.put(TEST_CLASS, testClass)
            LOG.info("========== TEST CLASS STARTED: {} ==========", testClass)
          }
      is MethodSource ->
          LOG.info(
              "---------- TEST STARTED: {}#{} ----------",
              classSimpleName(source.className),
              source.methodName)
      else -> {}
    }
  }

  override fun executionFinished(
      testIdentifier: TestIdentifier,
      testExecutionResult: TestExecutionResult
  ) {
    when (val source = testIdentifier.source.getOrNull()) {
      is MethodSource ->
          LOG.info(
              "---------- TEST FINISHED: {}#{} -> {} ----------",
              classSimpleName(source.className),
              source.methodName,
              testExecutionResult.status)
      is ClassSource ->
          if (testIdentifier.isContainer) {
            LOG.info(
                "========== TEST CLASS FINISHED: {} -> {} ==========",
                classSimpleName(source.className),
                testExecutionResult.status)
            ThreadContext.remove(TEST_CLASS)
          }
      else -> {}
    }
  }
}
