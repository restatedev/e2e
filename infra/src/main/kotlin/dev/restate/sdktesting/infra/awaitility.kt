// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.awaitility.core.ConditionFactory

private val LOG = LogManager.getLogger("dev.restate.sdktesting.infra.awaitility")

infix fun ConditionFactory.withTimeout(timeout: Duration): ConditionFactory =
    this.timeout(timeout.toJavaDuration())

/**
 * Coroutine-friendly variant of awaitility's `untilAsserted`: the assertion block is a suspend
 * function, polled on [Dispatchers.IO] while preserving the caller's coroutine context.
 */
suspend infix fun ConditionFactory.untilAsserted(fn: suspend () -> Unit) {
  withContext(currentCoroutineContext() + Dispatchers.IO) {
    val coroutineContext = currentCoroutineContext()
    this@untilAsserted.ignoreExceptions()
        .logging { LOG.info(it) }
        .pollInSameThread()
        .untilAsserted { runBlocking(coroutineContext) { fn() } }
  }
}
