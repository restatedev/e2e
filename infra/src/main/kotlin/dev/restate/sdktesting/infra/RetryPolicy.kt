// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

// Perhaps at some point we could autogenerate these from the openapi doc and also remove the need
// to manually implement these serialization routines
sealed class RetryPolicy {

  abstract fun toInvokerSetupEnv(): Map<String, String>

  object None : RetryPolicy() {
    override fun toInvokerSetupEnv(): Map<String, String> {
      return mapOf(
          "RESTATE_DEFAULT_RETRY_POLICY__MAX_ATTEMPTS" to "1",
          "RESTATE_DEFAULT_RETRY_POLICY__ON_MAX_ATTEMPTS" to "kill",
      )
    }
  }

  class FixedDelay(private val interval: String, private val maxAttempts: Int) : RetryPolicy() {
    override fun toInvokerSetupEnv(): Map<String, String> {
      return mapOf(
          "RESTATE_DEFAULT_RETRY_POLICY__INITIAL_INTERVAL" to interval,
          "RESTATE_DEFAULT_RETRY_POLICY__MAX_INTERVAL" to interval,
          "RESTATE_DEFAULT_RETRY_POLICY__EXPONENTIATION_FACTOR" to "1.0",
          "RESTATE_DEFAULT_RETRY_POLICY__MAX_ATTEMPTS" to maxAttempts.toString(),
          "RESTATE_DEFAULT_RETRY_POLICY__ON_MAX_ATTEMPTS" to "kill",
      )
    }
  }
}
