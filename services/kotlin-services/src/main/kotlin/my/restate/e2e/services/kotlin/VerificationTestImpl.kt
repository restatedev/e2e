// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services.kotlin

import dev.restate.sdk.kotlin.ObjectContext

class VerificationTestImpl : VerificationTest {
  override suspend fun doThis(ctx: ObjectContext, param: String): String {
    TODO("Not yet implemented")
  }
}
