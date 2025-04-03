// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.junit

import dev.restate.sdktesting.junit.ExecutionResult.TestResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull
import kotlin.time.TimeSource
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

class ExecutionResultCollector(private val testSuite: String) : TestExecutionListener {
  private var testPlan: TestPlan? = null
  private val classesResults: ConcurrentHashMap<TestIdentifier, TestResult> = ConcurrentHashMap()
  private val testResults: ConcurrentHashMap<TestIdentifier, TestResult> = ConcurrentHashMap()

  @Volatile private var timeStarted: TimeSource.Monotonic.ValueTimeMark? = null
  @Volatile private var timeFinished: TimeSource.Monotonic.ValueTimeMark? = null

  /** Get the summary generated by this listener. */
  val results: ExecutionResult
    get() {
      return ExecutionResult(
          testSuite,
          testPlan!!,
          classesResults.toMap(),
          testResults.toMap(),
          timeStarted!!,
          timeFinished!!)
    }

  override fun testPlanExecutionStarted(testPlan: TestPlan) {
    this.testPlan = testPlan
    this.timeStarted = TimeSource.Monotonic.markNow()
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    this.timeFinished = TimeSource.Monotonic.markNow()
  }

  override fun executionFinished(
      testIdentifier: TestIdentifier,
      testExecutionResult: TestExecutionResult
  ) {
    if (testIdentifier.source.getOrNull() is MethodSource && testIdentifier.isTest) {
      testResults[testIdentifier] = testExecutionResult.toTestResult()
    } else if (testIdentifier.source.getOrNull() is ClassSource) {
      classesResults[testIdentifier] = testExecutionResult.toTestResult()
    }
  }

  private fun TestExecutionResult.toTestResult(): TestResult {
    return when (this.status!!) {
      TestExecutionResult.Status.SUCCESSFUL -> ExecutionResult.Succeeded
      TestExecutionResult.Status.ABORTED -> ExecutionResult.Aborted
      TestExecutionResult.Status.FAILED -> ExecutionResult.Failed(this.throwable.getOrNull())
    }
  }
}
