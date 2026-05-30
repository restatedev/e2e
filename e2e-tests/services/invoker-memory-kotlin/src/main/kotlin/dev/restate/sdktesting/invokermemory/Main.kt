// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.invokermemory

import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.endpoint.journalRetention
import kotlin.time.Duration.Companion.seconds

fun main() {
  val endpointBuilder =
      Endpoint.bind(MemoryPressureService()) { it.journalRetention = 0.seconds }
          .bind(StatefulObject()) { it.journalRetention = 0.seconds }

  val port = System.getenv("PORT")?.toIntOrNull() ?: 9080

  // Starts the Vert.x HTTP/2 server and joins on the bind future. Vert.x event-loop
  // threads are non-daemon, so the JVM stays alive once this returns.
  RestateHttpServer.listen(endpointBuilder, port)
}
