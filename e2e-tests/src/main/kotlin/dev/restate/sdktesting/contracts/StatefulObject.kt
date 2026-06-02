// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.contracts

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.VirtualObject

/**
 * Contract for the virtual object used by `InvokerMemoryTest`. The implementation lives in the Rust
 * crate at `e2e-tests/services/invoker-memory-rs` and is shipped as
 * `ghcr.io/restatedev/e2e-invoker-memory-rs`. If you change a handler name or signature here,
 * update the Rust trait in lockstep — see that crate's README for the mapping table.
 */
@VirtualObject
@Name("StatefulObject")
interface StatefulObject {
  @Handler suspend fun initState(input: String)

  @Handler suspend fun readState(input: String): Int

  @Handler suspend fun readLargeState(input: String): Int
}
