// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import dev.restate.common.InvocationOptions
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.apache.logging.log4j.kotlin.additionalLoggingContext

/**
 * Invocation options that attach a random idempotency key. Use on essentially every client call.
 */
val idempotentCallOptions: InvocationOptions.Builder.() -> Unit = {
  idempotencyKey = UUID.randomUUID().toString()
}

/** [kotlinx.coroutines.test.runTest] wrapper that propagates the test's log4j context. */
fun runTest(timeout: Duration = 60.seconds, testBody: suspend TestScope.() -> Unit) =
    runTest(context = additionalLoggingContext(), timeout = timeout, testBody = testBody)
