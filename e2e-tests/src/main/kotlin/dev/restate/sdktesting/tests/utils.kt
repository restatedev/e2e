// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.admin.api.DeploymentApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.client.ApiException
import dev.restate.admin.model.RegisterDeploymentRequest
import dev.restate.admin.model.RegisterHttpDeploymentRequest
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.http.vertx.RestateHttpServer
import io.vertx.core.http.HttpServer
import java.net.URI
import java.util.concurrent.TimeUnit
import org.apache.logging.log4j.LogManager
import org.awaitility.Awaitility
import org.testcontainers.Testcontainers

private val LOG = LogManager.getLogger("dev.restate.sdktesting.tests")

/** Convert an Int to its value in bytes (kilobytes). */
inline val Int.kb: Int
  get() = this * 1024

/**
 * Retries a block that may throw ApiException with 503 status due to leadership changes. Uses a
 * 30-second timeout to stay well under the default 60-second test timeout. Only retries on 503
 * errors; other errors are propagated immediately.
 */
fun <T> retryOnServiceUnavailable(block: () -> T): T {
  return Awaitility.await()
      .atMost(30, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .ignoreExceptionsMatching { e -> e is ApiException && e.code == 503 }
      .until({ block() }) { true }
}

/** Generate a random alphanumeric string of the given length. Resists compression. */
fun randomString(length: Int): String {
  val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
  return String(CharArray(length) { allowedChars.random() })
}

/**
 * Starts a local Restate HTTP server for a given Endpoint and exposes the port to Testcontainers.
 * Returns an AutoCloseable handle that contains the URI and closes the server on close().
 */
class LocalEndpointHandle
internal constructor(val uri: String, val deploymentId: String, private val server: HttpServer) :
    AutoCloseable {
  override fun close() {
    server.close()
  }
}

fun startAndRegisterLocalEndpoint(endpoint: Endpoint, adminURI: URI): LocalEndpointHandle {
  val server: HttpServer = RestateHttpServer.fromEndpoint(endpoint)
  server.listen(0).toCompletionStage().toCompletableFuture().join()
  val port = server.actualPort()
  LOG.debug("Started local endpoint on port {}", port)
  Testcontainers.exposeHostPorts(port)
  val uri = "http://host.testcontainers.internal:$port"

  // Register the new endpoint with the runtime
  val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
  val deploymentApi = DeploymentApi(adminClient)

  val deploymentId =
      try {
        deploymentApi
            .createDeployment(
                RegisterDeploymentRequest(
                    RegisterHttpDeploymentRequest().uri(URI.create(uri)).force(false)))
            .id
      } catch (e: Exception) {
        LOG.error("Failed to register new deployment {}: {}", uri, e.message)
        throw e
      }

  return LocalEndpointHandle(uri, deploymentId, server)
}
