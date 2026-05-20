// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat

@Serializable
data class IngressSendResponse(
    @SerialName("invocationId") val invocationId: String,
    val status: String? = null,
)

private val ingressJson = Json { ignoreUnknownKeys = true }

/**
 * Send a scoped invocation to a `@Service` via the new `/restate/scope/{scope}/send/...` ingress
 * path. Returns the assigned invocation id.
 *
 * TODO: replace with the ingress client API once it supports scope. The scoped feature is currently
 *   exposed for `@Service` only, so this helper does not accept a virtual object key.
 */
suspend fun sendInvocationWithScope(
    ingressURI: URI,
    scope: String,
    service: String,
    handler: String,
    body: String,
    idempotencyKey: String = UUID.randomUUID().toString(),
): String {
  val request =
      HttpRequest.newBuilder()
          .uri(ingressURI.resolve("restate/scope/$scope/send/$service/$handler"))
          .header("Content-Type", "application/json")
          .header("idempotency-key", idempotencyKey)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build()

  val response =
      HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
  assertThat(response.statusCode())
      .withFailMessage {
        "Unexpected ingress response: ${response.statusCode()} ${response.body()}"
      }
      .isBetween(200, 299)
  return ingressJson.decodeFromString<IngressSendResponse>(response.body()).invocationId
}
