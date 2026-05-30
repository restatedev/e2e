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
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.kotlin.runBlock

@Service
@Name("MemoryPressureService")
class MemoryPressureService {
  @Handler
  suspend fun generate(input: String): String {
    for (i in 0 until 10) {
      runBlock { randomString(64.kb) }
    }
    return "ok-$input"
  }

  @Handler
  suspend fun generateOversized(input: String): String {
    // Single side effect producing 512KiB — exceeds the 256KiB per-invocation memory limit.
    // The invocation can never make progress and should be paused by the server.
    runBlock { randomString(512.kb) }
    return "ok-$input"
  }
}
