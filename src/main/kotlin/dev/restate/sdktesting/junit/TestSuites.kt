// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.junit

object TestSuites {
  val DEFAULT_SUITE = TestSuite("default", emptyMap(), "none() | always-suspending")
  val THREE_NODES_SUITE =
      TestSuite(
          "threeNodes",
          mapOf(
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "4",
          ),
          "(none() | always-suspending) & !only-single-node",
          3)
  private val ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "alwaysSuspending",
          mapOf("RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s"),
          "always-suspending | only-always-suspending")
  private val THREE_NODES_ALWAYS_SUSPENDING_SUITE =
      TestSuite(
          "threeNodesAlwaysSuspending",
          mapOf(
              "RESTATE_WORKER__INVOKER__INACTIVITY_TIMEOUT" to "0s",
              "RESTATE_DEFAULT_NUM_PARTITIONS" to "4",
          ),
          "(always-suspending | only-always-suspending) & !only-single-node",
          3)

  fun allSuites(): List<TestSuite> {
    return listOf(
        DEFAULT_SUITE,
        THREE_NODES_SUITE,
        ALWAYS_SUSPENDING_SUITE,
        THREE_NODES_ALWAYS_SUSPENDING_SUITE)
  }

  fun resolveSuites(suite: String?): List<TestSuite> {
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
