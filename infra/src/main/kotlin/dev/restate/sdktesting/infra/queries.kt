// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import dev.restate.admin.api.RuleApi
import dev.restate.admin.client.ApiClient
import dev.restate.admin.model.DeleteRuleRequest
import dev.restate.admin.model.RuleResponse
import dev.restate.admin.model.UpsertRuleRequest
import dev.restate.admin.model.UserLimits
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await as awaitCompletable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias

/** JSON parser shared by the admin `/query` and `restatectl` result decoders. */
private val queryJson = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
}

// ---------------------------------------------------------------------------------------------
// Rule book
// ---------------------------------------------------------------------------------------------

private fun ruleApi(adminURI: URI): RuleApi =
    RuleApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port))

/** Upsert a single rule that caps action concurrency for a given pattern. */
fun upsertActionConcurrencyRule(
    adminURI: URI,
    pattern: String,
    actionConcurrency: Int
): RuleResponse {
  val req =
      UpsertRuleRequest().pattern(pattern).limits(UserLimits().actionConcurrency(actionConcurrency))
  return ruleApi(adminURI).upsertRules(listOf(req)).single()
}

/** Bulk-delete rules by pattern. Returns the patterns that were actually removed. */
fun bulkDeleteRules(adminURI: URI, patterns: List<String>): List<String> {
  if (patterns.isEmpty()) return emptyList()
  val reqs = patterns.map { DeleteRuleRequest().pattern(it) }
  return ruleApi(adminURI).bulkDeleteRules(reqs)
}

/**
 * One row of `partition_state` as returned by `restatectl sql --json`.
 *
 * `partition_state` is an internal cluster-ctrl table not exposed on the admin `/query` port — see
 * https://github.com/restatedev/restate/pull/4783 — so we have to shell out to `restatectl` inside
 * the runtime container instead.
 */
@Serializable
data class PartitionStateEntry(
    @SerialName("partition_id") val partitionId: Long,
    @SerialName("plain_node_id") val plainNodeId: String,
    @SerialName("applied_rule_book_version") val appliedRuleBookVersion: Long? = null,
)

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
  return queryJson.decodeFromString(stdout)
}

/**
 * Block until every partition processor reports `applied_rule_book_version >= expectedVersion`.
 *
 * After an admin upsert succeeds, the new rule book still has to propagate from the metadata store
 * to each partition processor's state machine; without this wait, scoped invocations issued
 * immediately after the upsert may race the rule and run unthrottled.
 *
 * Pass the `version` from the [RuleResponse] returned by upsert: for a freshly-created rule the
 * per-rule version equals the post-bump rule-book version, so it's a safe lower bound to wait for.
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

// ---------------------------------------------------------------------------------------------
// sys_journal / sys_invocation queries (via the admin `/query` endpoint)
// ---------------------------------------------------------------------------------------------

/** Data classes for sys_journal query result */
@Serializable data class JournalQueryResult(val rows: List<SysJournalEntry> = emptyList())

@Serializable
data class SysJournalEntry(val index: Int, @SerialName("entry_type") val entryType: String)

/** Data classes for sys_invocation query result */
@Serializable data class InvocationQueryResult(val rows: List<SysInvocationEntry> = emptyList())

@Serializable data class SysInvocationEntry(val id: String, val status: String)

private suspend fun queryAdmin(adminURI: URI, query: String): String {
  val request =
      HttpRequest.newBuilder()
          .uri(URI.create("http://${adminURI.host}:${adminURI.port}/query"))
          .header("accept", "application/json")
          .header("content-type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""{"query": "$query"}"""))
          .build()
  return HttpClient.newHttpClient()
      .sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .awaitCompletable()
      .body()
}

/**
 * Queries the sys_journal table for a given invocation ID and returns the parsed result.
 *
 * @param invocationId The ID of the invocation to query
 * @param adminURI The URI of the Restate admin API
 * @return The parsed result of the query
 */
suspend fun getJournal(adminURI: URI, invocationId: String): JournalQueryResult {
  val body =
      queryAdmin(adminURI, "SELECT index, entry_type FROM sys_journal WHERE id = '$invocationId'")
  return queryJson.decodeFromString<JournalQueryResult>(body)
}

/**
 * Queries the sys_invocation table for a given invocation ID and returns the parsed result.
 *
 * @param invocationId The ID of the invocation to query
 * @param adminURI The URI of the Restate admin API
 * @return The parsed result of the query containing invocation status information
 */
suspend fun getInvocationStatus(adminURI: URI, invocationId: String): SysInvocationEntry {
  val body =
      queryAdmin(adminURI, "SELECT id, status FROM sys_invocation WHERE id = '$invocationId'")
  val queryResult = queryJson.decodeFromString<InvocationQueryResult>(body)
  assertThat(queryResult.rows).size().isEqualTo(1)
  return queryResult.rows[0]
}

/** Queries the sys_invocation table */
suspend fun getAllInvocations(adminURI: URI, filter: String? = null): List<SysInvocationEntry> {
  val query =
      if (filter.isNullOrBlank()) {
        "SELECT id, status FROM sys_invocation"
      } else {
        "SELECT id, status FROM sys_invocation WHERE $filter"
      }
  return queryJson.decodeFromString<InvocationQueryResult>(queryAdmin(adminURI, query)).rows
}
