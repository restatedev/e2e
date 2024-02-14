// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE

package dev.restate.e2e.utils

import com.github.dockerjava.api.command.InspectContainerResponse
import java.net.URL
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget

internal open class NotCachedContainerInfo(private val delegate: WaitStrategyTarget) :
    WaitStrategyTarget by delegate {
  override fun getContainerInfo(): InspectContainerResponse {
    // Need to ask the delegate to avoid recursive call to getContainerInfo()
    val containerId = this.delegate.containerId
    return dockerClient.inspectContainerCmd(containerId).exec()
  }
}

internal class WaitOnSpecificPortsTarget(
    private val ports: List<Int>,
    delegate: WaitStrategyTarget
) : NotCachedContainerInfo(delegate) {
  override fun getExposedPorts(): MutableList<Int> {
    return ports.toMutableList()
  }
}

fun waitForServicesBeingAvailable(services: Collection<String>, ingressURL: URL) {
  val healthCheckURL =
      URL(
          ingressURL.protocol,
          ingressURL.host,
          ingressURL.port,
          "/${RestateDeployer.HEALTH_CHECK_SERVICE}")

  for (service in services) {
    val body = mapOf("service" to service)

    await
        .untilCallTo { JsonUtils.postJsonRequest(healthCheckURL.toString(), body) }
        .matches { response ->
          response!!.statusCode() == 200 &&
              response!!.body().get("status").asText().equals("SERVING")
        }
  }
}
