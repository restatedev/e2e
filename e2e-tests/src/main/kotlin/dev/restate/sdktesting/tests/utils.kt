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
import dev.restate.common.InvocationOptions
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdktesting.infra.ContainerHandle
import io.vertx.core.http.HttpServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
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
import org.awaitility.Awaitility
import org.awaitility.core.ConditionFactory
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.testcontainers.Testcontainers

private val LOG = LogManager.getLogger("dev.restate.sdktesting.tests")

/** Convert an Int to its value in bytes (kilobytes). */
inline val Int.kb: Int
  get() = this * 1024

infix fun ConditionFactory.withTimeout(timeout: Duration): ConditionFactory =
    this.timeout(timeout.toJavaDuration())

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

val idempotentCallOptions: InvocationOptions.Builder.() -> Unit = {
  idempotencyKey = UUID.randomUUID().toString()
}

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

/** Data classes for sys_journal query result */
@Serializable data class JournalQueryResult(val rows: List<SysJournalEntry> = emptyList())

@Serializable
data class SysJournalEntry(val index: Int, @SerialName("entry_type") val entryType: String)

/** Data classes for sys_invocation query result */
@Serializable data class InvocationQueryResult(val rows: List<SysInvocationEntry> = emptyList())

@Serializable data class SysInvocationEntry(val id: String, val status: String)

/**
 * One row of `partition_state` as returned by `restatectl sql --json`.
 *
 * `partition_state` is an internal cluster-ctrl table not exposed on the admin `/query` port — see
 * https://github.com/restatedev/restate/pull/4783 — so we have to shell out to `restatectl`
 * inside the runtime container instead.
 */
@Serializable
data class PartitionStateEntry(
    @SerialName("partition_id") val partitionId: Long,
    @SerialName("plain_node_id") val plainNodeId: String,
    @SerialName("applied_rule_book_version") val appliedRuleBookVersion: Long? = null,
)

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
 * Returns one row per partition processor by execing `restatectl sql --json` inside the runtime
 * container.
 *
 * `partition_state` is an internal cluster-ctrl table, so this can't go through the admin `/query`
 * endpoint. `applied_rule_book_version` is `null` until the partition processor has observed its
 * first `UpsertRuleBook` (see Restate PR #4783).
 */
suspend fun getAllPartitionStates(runtimeHandle: ContainerHandle): List<PartitionStateEntry> {
  val result =
      withContext(Dispatchers.IO) {
        runtimeHandle.container.execInContainer(
            "restatectl",
            "sql",
            "--json",
            "SELECT partition_id, plain_node_id, applied_rule_book_version FROM partition_state")
      }
  check(result.exitCode == 0) {
    "restatectl sql exited with ${result.exitCode}: stdout=${result.stdout}, stderr=${result.stderr}"
  }
  // `restatectl sql --json` only writes the row count + timing to stderr; stdout is a single
  // arrow JSON array. An empty result set is "" (no rows ever printed), so guard for that.
  val stdout = result.stdout.trim()
  if (stdout.isEmpty()) return emptyList()
  return sysQueryJson.decodeFromString(stdout)
}

/**
 * Block until every partition processor reports `applied_rule_book_version >= expectedVersion`.
 *
 * After an admin upsert succeeds, the new rule book still has to propagate from the metadata store
 * to each partition processor's state machine; without this wait, scoped invocations issued
 * immediately after the upsert may race the rule and run unthrottled.
 *
 * Pass the `version` from the [dev.restate.admin.model.RuleResponse] returned by upsert: for a
 * freshly-created rule the per-rule version equals the post-bump rule-book version, so it's a safe
 * lower bound to wait for.
 */
suspend fun awaitRuleBookApplied(
    runtimeHandle: ContainerHandle,
    expectedVersion: Int,
    timeout: Duration = 30.seconds
) {
  await withAlias
      "partition_state.applied_rule_book_version >= $expectedVersion on all partitions" withTimeout
      timeout untilAsserted
      {
        val states = getAllPartitionStates(runtimeHandle)
        assertThat(states).isNotEmpty
        assertThat(states).allSatisfy { row ->
          assertThat(row.appliedRuleBookVersion)
              .withFailMessage(
                  "partition %d on node %s has not applied rule-book version %d yet (currently %s)",
                  row.partitionId,
                  row.plainNodeId,
                  expectedVersion,
                  row.appliedRuleBookVersion)
              .isNotNull
              .isGreaterThanOrEqualTo(expectedVersion.toLong())
        }
      }
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
