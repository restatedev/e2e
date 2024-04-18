// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services.kotlin

import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder

fun main() {
  val services =
      checkNotNull(System.getenv("SERVICES")) {
            "SERVICES env variable needs to specify which service to run."
          }
          .split(",".toRegex())
          .dropLastWhile { it.isEmpty() }
          .toTypedArray()

  val restateHttpEndpointBuilder = RestateHttpEndpointBuilder.builder()
  for (svc in services) {
    val fqsn = svc.trim { it <= ' ' }
    when (fqsn) {
      VerificationTestClient.SERVICE_NAME -> restateHttpEndpointBuilder.bind(VerificationTestImpl())
    }
  }

  restateHttpEndpointBuilder.buildAndListen()
}
