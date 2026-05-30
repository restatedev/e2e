// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.invokermemory

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state

@VirtualObject
@Name("StatefulObject")
class StatefulObject {
  @Handler
  suspend fun initState(input: String) {
    // Store two 32KiB state entries (64KiB total per virtual object)
    state().set("state-a", randomString(32.kb))
    state().set("state-b", randomString(32.kb))
  }

  @Handler
  suspend fun readState(input: String): Int {
    val a = state().get<String>("state-a") ?: ""
    val b = state().get<String>("state-b") ?: ""
    return a.length + b.length
  }

  @Handler
  suspend fun readLargeState(input: String): Int {
    val data = state().get<String>("large-state") ?: ""
    return data.length
  }
}
