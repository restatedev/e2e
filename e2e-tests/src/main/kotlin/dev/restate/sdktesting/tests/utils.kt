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
import dev.restate.sdktesting.infra.exposeHostPort
import io.vertx.core.http.HttpServer
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.apache.logging.log4j.LogManager
import org.awaitility.Awaitility

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

/** Resume an invocation using the query encoding expected by Restate's untagged deployment enum. */
fun resumeInvocation(adminURI: URI, invocationId: String, deployment: String) {
  sendPatch(
      adminURI.resolve(
          "invocations/${urlEncode(invocationId)}/resume?deployment=${urlEncode(deployment)}"
      )
  )
}

/** Restart an invocation using the query encoding expected by Restate's deployment enum. */
fun restartAsNewInvocation(
    adminURI: URI,
    invocationId: String,
    from: Int?,
    deployment: String,
): String {
  val query =
      listOfNotNull(from?.let { "from=$it" }, "deployment=${urlEncode(deployment)}")
          .joinToString("&")
  val response =
      sendPatch(adminURI.resolve("invocations/${urlEncode(invocationId)}/restart-as-new?$query"))
  return ApiClient().objectMapper.readTree(response).get("new_invocation_id").asText()
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

private fun sendPatch(uri: URI): String {
  val request =
      HttpRequest.newBuilder().uri(uri).method("PATCH", HttpRequest.BodyPublishers.noBody()).build()
  val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
  if (response.statusCode() / 100 != 2) {
    throw ApiException(response.statusCode(), response.headers(), response.body())
  }
  return response.body()
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
  exposeHostPort(port)
  val uri = "http://host.testcontainers.internal:$port"

  // Register the new endpoint with the runtime
  val adminClient = ApiClient().setHost(adminURI.host).setPort(adminURI.port)
  val deploymentApi = DeploymentApi(adminClient)

  val deploymentId =
      try {
        deploymentApi
            .createDeployment(
                RegisterDeploymentRequest(
                    RegisterHttpDeploymentRequest().uri(URI.create(uri)).force(false)
                )
            )
            .id
      } catch (e: Exception) {
        LOG.error("Failed to register new deployment {}: {}", uri, e.message)
        throw e
      }

  return LocalEndpointHandle(uri, deploymentId, server)
}
