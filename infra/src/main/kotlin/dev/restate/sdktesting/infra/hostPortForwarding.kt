// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import java.util.AbstractMap
import java.util.Optional
import java.util.concurrent.TimeUnit
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.Testcontainers
import org.testcontainers.containers.PortForwardingContainer

/**
 * `PortForwardingContainer.exposedPorts`: Testcontainers records a port here *before* it boots sshd
 * and requests the tunnel, and never removes it on failure, so a plain retry is a silent no-op and
 * later containers miss the `host.testcontainers.internal` extra host.
 */
private val exposedPortsField =
    PortForwardingContainer::class.java.getDeclaredField("exposedPorts").apply {
      isAccessible = true
    }

/** `PortForwardingContainer.getNetwork()`: empty when the sshd container isn't running. */
private val tunnelNetworkMethod =
    PortForwardingContainer::class.java.getDeclaredMethod("getNetwork").apply {
      isAccessible = true
    }

private fun tunnelIsUp(): Boolean =
    (tunnelNetworkMethod.invoke(PortForwardingContainer.INSTANCE) as Optional<*>).isPresent

private fun forgetExposedPort(port: Int) {
  @Suppress("UNCHECKED_CAST")
  val exposedPorts =
      exposedPortsField.get(PortForwardingContainer.INSTANCE) as MutableSet<Map.Entry<Int, Int>>
  exposedPorts.remove(AbstractMap.SimpleImmutableEntry(port, port))
}

/**
 * Makes [port] on the host reachable from within the containers, at
 * `http://host.testcontainers.internal:$port`.
 *
 * Retrying wrapper around [Testcontainers.exposeHostPorts]: the first call lazily boots the sshd
 * container Testcontainers tunnels host ports through, and that container occasionally fails to
 * come up when the docker daemon is under load, which would otherwise take down the whole test
 * class. Testcontainers doesn't cache a failed sshd start, but it does remember the port as
 * exposed, so we forget it before retrying (see [exposedPortsField]).
 */
fun exposeHostPort(port: Int) {
  Unreliables.retryUntilSuccess(30, TimeUnit.SECONDS) {
    try {
      Testcontainers.exposeHostPorts(port)
      check(tunnelIsUp()) { "the Testcontainers sshd container is not running" }
      true
    } catch (e: Exception) {
      forgetExposedPort(port)
      Thread.sleep(500)
      throw IllegalStateException("Error when exposing host port $port to the containers", e)
    }
  }
}
