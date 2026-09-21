// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import java.util.concurrent.TimeUnit
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.Testcontainers

/**
 * Makes [port] on the host reachable from within the containers, at
 * `http://host.testcontainers.internal:$port`.
 *
 * Retrying wrapper around [Testcontainers.exposeHostPorts]: the first call lazily boots the sshd
 * container Testcontainers tunnels host ports through, and that container occasionally fails to
 * come up when the docker daemon is under load, which would otherwise take down the whole test
 * class. Testcontainers doesn't cache a failed start, so simply retrying is enough.
 */
fun exposeHostPort(port: Int) {
  Unreliables.retryUntilSuccess(30, TimeUnit.SECONDS) {
    try {
      Testcontainers.exposeHostPorts(port)
      true
    } catch (e: Exception) {
      Thread.sleep(500)
      throw IllegalStateException("Error when exposing host port $port to the containers", e)
    }
  }
}
