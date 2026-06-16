// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.junit

import dev.restate.sdktesting.tests.AwakeableIngressEndpointTest
import dev.restate.sdktesting.tests.AwakeableLeaderTransferTest
import dev.restate.sdktesting.tests.BackwardCompatibilityTest
import dev.restate.sdktesting.tests.ConcurrencyLimitTest
import dev.restate.sdktesting.tests.ForwardCompatibilityTest
import dev.restate.sdktesting.tests.IngressTest
import dev.restate.sdktesting.tests.InvokerMemoryTest
import dev.restate.sdktesting.tests.JournalRetentionTest
import dev.restate.sdktesting.tests.KafkaTest
import dev.restate.sdktesting.tests.OpenAPITest
import dev.restate.sdktesting.tests.PauseResumeChangingDeploymentTest
import dev.restate.sdktesting.tests.PauseResumeTest
import dev.restate.sdktesting.tests.RestartAsNewInvocationTest
import dev.restate.sdktesting.tests.ScopeIsolationTest
import dev.restate.sdktesting.tests.StatePatchingTest
import dev.restate.sdktesting.tests.TracingTest
import dev.restate.sdktesting.tests.UpgradeWithInFlightInvocation
import dev.restate.sdktesting.tests.UpgradeWithNewInvocation

object TestSuites : SuiteProvider {
  override val defaultSuite: TestSuite
    get() = DEFAULT_SUITE

  val DEFAULT_SUITE =
      TestSuite(
          "default",
          emptyMap(),
          listOf(
              clazz<AwakeableIngressEndpointTest>(),
              clazz<ConcurrencyLimitTest>(),
              clazz<IngressTest>(),
              clazz<InvokerMemoryTest>(),
              clazz<JournalRetentionTest>(),
              clazz<KafkaTest>(),
              clazz<OpenAPITest>(),
              clazz<PauseResumeChangingDeploymentTest>(),
              clazz<PauseResumeTest>(),
              clazz<RestartAsNewInvocationTest>(),
              clazz<ScopeIsolationTest>(),
              clazz<StatePatchingTest>(),
              clazz<TracingTest>(),
              clazz<UpgradeWithNewInvocation>(),
              clazz<UpgradeWithInFlightInvocation>(),
          ),
          useNewClient = true)

  val THREE_NODES_SUITE =
      TestSuite(
          "threeNodes",
          mapOf(
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "4",
          ),
          listOf(
              clazz<ConcurrencyLimitTest>(),
              clazz<ScopeIsolationTest>(),
              clazz<AwakeableIngressEndpointTest>(),
              clazz<AwakeableLeaderTransferTest>(),
              clazz<IngressTest>(),
              clazz<JournalRetentionTest>(),
              clazz<PauseResumeTest>(),
              clazz<RestartAsNewInvocationTest>(),
              clazz<StatePatchingTest>(),
              clazz<TracingTest>(),
          ),
          restateNodes = 3,
          useNewClient = true)

  private val ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "alwaysSuspending",
          mapOf("RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s"),
          listOf(
              clazz<InvokerMemoryTest>(),
              clazz<PauseResumeChangingDeploymentTest>(),
              clazz<UpgradeWithNewInvocation>(),
              clazz<UpgradeWithInFlightInvocation>(),
          ),
          useNewClient = true)

  private val THREE_NODES_ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "threeNodesAlwaysSuspending",
          mapOf(
              "RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s",
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "4",
          ),
          listOf(clazz<AwakeableLeaderTransferTest>()),
          restateNodes = 3,
          useNewClient = true)

  private val VERSION_COMPATIBILITY_SUITE =
      TestSuite(
          "versionCompat",
          emptyMap(),
          listOf(clazz<BackwardCompatibilityTest>(), clazz<ForwardCompatibilityTest>()),
          useNewClient = false)

  private val OLD_INGRESS_API_SUITE =
      TestSuite(
          "oldIngressAPI",
          DEFAULT_SUITE.additionalEnvs,
          DEFAULT_SUITE.selectors,
          useNewClient = false)

  override fun allSuites(): List<TestSuite> {
    return listOf(
        DEFAULT_SUITE,
        THREE_NODES_SUITE,
        ALWAYS_SUSPENDING_SUITE,
        THREE_NODES_ALWAYS_SUSPENDING_SUITE,
        VERSION_COMPATIBILITY_SUITE,
        OLD_INGRESS_API_SUITE)
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
                    VERSION_COMPATIBILITY_SUITE.name -> listOf(VERSION_COMPATIBILITY_SUITE)
                    OLD_INGRESS_API_SUITE.name -> listOf(OLD_INGRESS_API_SUITE)
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
