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
import dev.restate.admin.model.RegisterDeploymentRequest
import dev.restate.admin.model.RegisterHttpDeploymentRequest
import dev.restate.common.RequestBuilder
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.http.vertx.RestateHttpServer
import io.vertx.core.http.HttpServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.kotlin.additionalLoggingContext
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.core.ConditionFactory
import org.testcontainers.Testcontainers

private val LOG = LogManager.getLogger("dev.restate.sdktesting.tests")

suspend infix fun ConditionFactory.untilAsserted(fn: suspend () -> Unit) {
  withContext(currentCoroutineContext() + Dispatchers.IO) {
    val coroutineContext = currentCoroutineContext()
    this@untilAsserted.ignoreExceptions()
        .logging { LOG.info(it) }
        .pollInSameThread()
        .untilAsserted { runBlocking(coroutineContext) { fn() } }
  }
}

fun runTest(timeout: Duration = 60.seconds, testBody: suspend TestScope.() -> Unit) =
    runTest(context = additionalLoggingContext(), timeout = timeout, testBody = testBody)

val idempotentCallOptions: RequestBuilder<*, *>.() -> Unit = {
  idempotencyKey = UUID.randomUUID().toString()
}

/** Data classes for sys_journal query result */
@Serializable data class JournalQueryResult(val rows: List<SysJournalEntry> = emptyList())

@Serializable
data class SysJournalEntry(val index: Int, @SerialName("entry_type") val entryType: String)

/** Data classes for sys_invocation query result */
@Serializable data class InvocationQueryResult(val rows: List<SysInvocationEntry> = emptyList())

@Serializable data class SysInvocationEntry(val id: String, val status: String)

/** JSON parser with configuration for sys_journal and sys_invocation query results */
private val sysQueryJson = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
}

/**
 * Queries the sys_journal table for a given invocation ID and returns the parsed result.
 *
 * @param invocationId The ID of the invocation to query
 * @param adminURI The URI of the Restate admin API
 * @return The parsed result of the query
 */
suspend fun getJournal(adminURI: URI, invocationId: String): JournalQueryResult {
  // Create the HTTP request to query sys_journal
  val request =
      HttpRequest.newBuilder()
          .uri(URI.create("http://${adminURI.host}:${adminURI.port}/query"))
          .header("accept", "application/json")
          .header("content-type", "application/json")
          .POST(
              HttpRequest.BodyPublishers.ofString(
                  """{"query": "SELECT index, entry_type FROM sys_journal WHERE id = '$invocationId'"}"""))
          .build()

  // Send the request and get the response
  val response =
      HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

  // Parse the response using Kotlin serialization
  return sysQueryJson.decodeFromString<JournalQueryResult>(response.body())
}

/**
 * Queries the sys_invocation table for a given invocation ID and returns the parsed result.
 *
 * @param invocationId The ID of the invocation to query
 * @param adminURI The URI of the Restate admin API
 * @return The parsed result of the query containing invocation status information
 */
suspend fun getInvocationStatus(adminURI: URI, invocationId: String): SysInvocationEntry {
  // Create the HTTP request to query sys_invocation
  val request =
      HttpRequest.newBuilder()
          .uri(URI.create("http://${adminURI.host}:${adminURI.port}/query"))
          .header("accept", "application/json")
          .header("content-type", "application/json")
          .POST(
              HttpRequest.BodyPublishers.ofString(
                  """{"query": "SELECT id, status FROM sys_invocation WHERE id = '$invocationId'"}"""))
          .build()

  // Send the request and get the response
  val response =
      HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

  // Parse the response using Kotlin serialization
  val queryResult = sysQueryJson.decodeFromString<InvocationQueryResult>(response.body())
  assertThat(queryResult.rows).size().isEqualTo(1)
  return queryResult.rows[0]
}

/** Queries the sys_invocation table */
suspend fun getAllInvocations(adminURI: URI, filter: String? = null): List<SysInvocationEntry> {
  val query =
      if (filter.isNullOrBlank()) {
        """{"query": "SELECT id, status FROM sys_invocation"}"""
      } else {
        """{"query": "SELECT id, status FROM sys_invocation WHERE $filter"}"""
      }

  // Create the HTTP request to query sys_invocation
  val request =
      HttpRequest.newBuilder()
          .uri(URI.create("http://${adminURI.host}:${adminURI.port}/query"))
          .header("accept", "application/json")
          .header("content-type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(query))
          .build()

  // Send the request and get the response
  val response =
      HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

  // Parse the response using Kotlin serialization
  return sysQueryJson.decodeFromString<InvocationQueryResult>(response.body()).rows
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
                RegisterDeploymentRequest(RegisterHttpDeploymentRequest().uri(uri).force(false)))
            .id
      } catch (e: Exception) {
        LOG.error("Failed to register new deployment {}: {}", uri, e.message)
        throw e
      }

  return LocalEndpointHandle(uri, deploymentId, server)
}
