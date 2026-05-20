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
