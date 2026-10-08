// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.contracts

import dev.restate.sdk.annotation.*
import kotlinx.serialization.Serializable

@VirtualObject
@Name("MapObject")
interface MapObject {

  @Serializable data class Entry(val key: String, val value: String)

  /**
   * Set value in map.
   *
   * The individual entries should be stored as separate Restate state keys, and not in a single
   * state key
   */
  @Handler suspend fun set(entry: Entry)

  /** Get value from map. */
  @Handler suspend fun get(key: String): String

  @Serializable data class ProjectRequest(val key: String, val pointer: String)

  /**
   * Get the value of [ProjectRequest.key] with a projection, available since service protocol V8.
   *
   * The value is parsed as a JSON document, and the projection applies to it the JSON pointer
   * [ProjectRequest.pointer] (RFC 6901). Returns the pointed value serialized as JSON, or an empty
   * string if the entry is missing or the pointer doesn't resolve.
   *
   * This MUST be implemented using get state with projection: the state value is read without
   * recording it in the journal, and only the projection result is recorded (e.g. as a `ctx.run`).
   */
  @Handler suspend fun getProject(request: ProjectRequest): String

  /** Clear all entries */
  @Handler suspend fun clearAll(): List<Entry>
}
