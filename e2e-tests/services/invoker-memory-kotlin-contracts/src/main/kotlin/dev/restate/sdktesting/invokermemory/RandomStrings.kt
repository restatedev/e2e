// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.invokermemory

/** Convert an Int to its value in bytes (kilobytes). */
internal inline val Int.kb: Int
  get() = this * 1024

/** Generate a random alphanumeric string of the given length. Resists compression. */
internal fun randomString(length: Int): String {
  val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
  return String(CharArray(length) { allowedChars.random() })
}
