// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.junit

import dev.restate.sdktesting.tests.CallOrdering
import dev.restate.sdktesting.tests.Cancellation
import dev.restate.sdktesting.tests.Combinators
import dev.restate.sdktesting.tests.Custom
import dev.restate.sdktesting.tests.Ingress
import dev.restate.sdktesting.tests.KillInvocation
import dev.restate.sdktesting.tests.KillRuntime
import dev.restate.sdktesting.tests.NonDeterminismErrors
import dev.restate.sdktesting.tests.ProxyRequestSigning
import dev.restate.sdktesting.tests.RunFlush
import dev.restate.sdktesting.tests.RunRetry
import dev.restate.sdktesting.tests.ServiceToServiceCommunication
import dev.restate.sdktesting.tests.ServiceToServiceScopeConcurrency
import dev.restate.sdktesting.tests.Signals
import dev.restate.sdktesting.tests.Sleep
import dev.restate.sdktesting.tests.SleepWithFailures
import dev.restate.sdktesting.tests.State
import dev.restate.sdktesting.tests.StopRuntime
import dev.restate.sdktesting.tests.UserErrors
import dev.restate.sdktesting.tests.WorkflowAPI

object TestSuites : SuiteProvider {
  override val defaultSuite: TestSuite
    get() = DEFAULT_SUITE

  val DEFAULT_SUITE =
      TestSuite(
          "default",
          emptyMap(),
          listOf(
              clazz<CallOrdering>(),
              clazz<Cancellation>(),
              clazz<Combinators>(),
              clazz<Custom>(),
              clazz<Ingress>(),
              clazz<KillInvocation>(),
              clazz<KillRuntime>(),
              clazz<ProxyRequestSigning>(),
              clazz<RunRetry>(),
              clazz<ServiceToServiceCommunication>(),
              clazz<ServiceToServiceScopeConcurrency>(),
              clazz<Sleep>(),
              clazz<SleepWithFailures>(),
              clazz<State>(),
              clazz<StopRuntime>(),
              clazz<UserErrors>(),
              clazz<WorkflowAPI>(),
              clazz<Signals>()))

  val THREE_NODES_SUITE =
      TestSuite(
          "threeNodes",
          mapOf(
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "4",
          ),
          listOf(
              clazz<CallOrdering>(),
              clazz<Cancellation>(),
              clazz<Combinators>(),
              clazz<Ingress>(),
              clazz<KillInvocation>(),
              clazz<ProxyRequestSigning>(),
              clazz<RunRetry>(),
              clazz<ServiceToServiceCommunication>(),
              clazz<ServiceToServiceScopeConcurrency>(),
              clazz<Sleep>(),
              clazz<SleepWithFailures>(),
              clazz<State>(),
              clazz<UserErrors>(),
              clazz<WorkflowAPI>(),
              clazz<Signals>()),
          3)

  private val ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "alwaysSuspending",
          mapOf("RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s"),
          listOf(
              clazz<Cancellation>(),
              clazz<Combinators>(),
              clazz<KillRuntime>(),
              clazz<NonDeterminismErrors>(),
              clazz<RunFlush>(),
              clazz<RunRetry>(),
              clazz<ServiceToServiceCommunication>(),
              clazz<Sleep>(),
              clazz<SleepWithFailures>(),
              clazz<State>(),
              clazz<StopRuntime>(),
              clazz<UserErrors>(),
              clazz<WorkflowAPI>(),
              clazz<Signals>()))

  private val THREE_NODES_ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "threeNodesAlwaysSuspending",
          mapOf(
              "RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s",
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "4",
          ),
          listOf(
              clazz<Cancellation>(),
              clazz<Combinators>(),
              clazz<RunFlush>(),
              clazz<RunRetry>(),
              clazz<ServiceToServiceCommunication>(),
              clazz<Sleep>(),
              clazz<SleepWithFailures>(),
              clazz<State>(),
              clazz<UserErrors>(),
              clazz<WorkflowAPI>(),
              clazz<Signals>()),
          3)

  private val SINGLE_THREAD_SINGLE_PARTITION_SUITE =
      TestSuite(
          "singleThreadSinglePartition",
          mapOf(
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "1",
              "RESTATE_DEFAULT_THREAD_POOL_SIZE" to "1",
          ),
          listOf(
              clazz<CallOrdering>(),
              clazz<Cancellation>(),
              clazz<Combinators>(),
              clazz<Ingress>(),
              clazz<KillInvocation>(),
              clazz<KillRuntime>(),
              clazz<ProxyRequestSigning>(),
              clazz<RunRetry>(),
              clazz<ServiceToServiceCommunication>(),
              clazz<Sleep>(),
              clazz<SleepWithFailures>(),
              clazz<State>(),
              clazz<StopRuntime>(),
              clazz<UserErrors>(),
              clazz<WorkflowAPI>(),
              clazz<Signals>()))

  private val LAZY_STATE_SUITE =
      TestSuite(
          "lazyState",
          mapOf(
              "RESTATE_WORKER__INVOKER__DISABLE_EAGER_STATE" to "true",
          ),
          listOf(clazz<State>()))

  private val LAZY_STATE_ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "lazyStateAlwaysSuspending",
          mapOf(
              "RESTATE_WORKER__INVOKER__DISABLE_EAGER_STATE" to "true",
              "RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s",
          ),
          listOf(clazz<State>()))

  private val PERSISTED_TIMERS_SUITE =
      TestSuite(
          "persistedTimers",
          mapOf("RESTATE_WORKER__NUM_TIMERS_IN_MEMORY_LIMIT" to "1"),
          listOf(clazz<Sleep>()))

  override fun allSuites(): List<TestSuite> {
    return listOf(
        DEFAULT_SUITE,
        THREE_NODES_SUITE,
        ALWAYS_SUSPENDING_SUITE,
        THREE_NODES_ALWAYS_SUSPENDING_SUITE,
        SINGLE_THREAD_SINGLE_PARTITION_SUITE,
        LAZY_STATE_SUITE,
        LAZY_STATE_ALWAYS_SUSPENDING_SUITE,
        PERSISTED_TIMERS_SUITE)
  }

  override fun resolveSuites(suite: String?): List<TestSuite> {
    return when (suite ?: "all") {
      "all" -> allSuites()
      else -> {
        var result = listOf<TestSuite>()
        for (configuration in suite!!.split(',')) {
          result =
              result +
                  when (configuration) {
                    DEFAULT_SUITE.name -> listOf(DEFAULT_SUITE)
                    THREE_NODES_SUITE.name -> listOf(THREE_NODES_SUITE)
                    ALWAYS_SUSPENDING_SUITE.name -> listOf(ALWAYS_SUSPENDING_SUITE)
                    THREE_NODES_ALWAYS_SUSPENDING_SUITE.name ->
                        listOf(THREE_NODES_ALWAYS_SUSPENDING_SUITE)
                    SINGLE_THREAD_SINGLE_PARTITION_SUITE.name ->
                        listOf(SINGLE_THREAD_SINGLE_PARTITION_SUITE)
                    LAZY_STATE_SUITE.name -> listOf(LAZY_STATE_SUITE)
                    LAZY_STATE_ALWAYS_SUSPENDING_SUITE.name ->
                        listOf(LAZY_STATE_ALWAYS_SUSPENDING_SUITE)
                    PERSISTED_TIMERS_SUITE.name -> listOf(PERSISTED_TIMERS_SUITE)
                    else -> {
                      throw IllegalArgumentException("Unexpected suite name $suite")
                    }
                  }
        }
        result
      }
    }
  }
}
