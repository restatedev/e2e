// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import dev.restate.client.Client
import dev.restate.client.Response
import dev.restate.client.SendResponse
import dev.restate.common.Request
import dev.restate.serde.TypeTag
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull
import org.apache.logging.log4j.LogManager

internal class LoggingClient(private val delegate: Client) : Client {

  companion object {
    private val LOG = LogManager.getLogger(LoggingClient::class.java)
    private val JSON = Json { prettyPrint = true }
  }

  override fun <Req, Res> callAsync(request: Request<Req, Res>): CompletableFuture<Response<Res>> {
    LOG.info("→ CALL {}", formatRequest(request))
    return delegate.callAsync(request).whenComplete { response, ex ->
      if (ex != null) {
        LOG.info("← CALL {} error: {}", request.target, ex.message)
      } else {
        LOG.info(
            "← CALL {} status={} headers={} response={}",
            request.target,
            response.statusCode(),
            response.headers().toLowercaseMap(),
            formatBody(response.response()))
      }
    }
  }

  override fun <Req, Res> sendAsync(
      request: Request<Req, Res>,
      delay: Duration?
  ): CompletableFuture<SendResponse<Res>> {
    LOG.info("→ SEND {}", formatRequest(request))
    return delegate.sendAsync(request, delay).whenComplete { response, ex ->
      if (ex != null) {
        LOG.info("← SEND {} error: {}", request.target, ex.message)
      } else {
        LOG.info(
            "← SEND {} status={} invocationId={} sendStatus={}",
            request.target,
            response.statusCode(),
            response.invocationId(),
            response.sendStatus())
      }
    }
  }

  // Delegate remaining abstract methods unchanged.

  override fun awakeableHandle(id: String): Client.AwakeableHandle = delegate.awakeableHandle(id)

  override fun <Res> invocationHandle(
      invocationId: String,
      resTypeTag: TypeTag<Res>
  ): Client.InvocationHandle<Res> = delegate.invocationHandle(invocationId, resTypeTag)

  override fun <Res> idempotentInvocationHandle(
      target: dev.restate.common.Target,
      idempotencyKey: String,
      resTypeTag: TypeTag<Res>
  ): Client.IdempotentInvocationHandle<Res> =
      delegate.idempotentInvocationHandle(target, idempotencyKey, resTypeTag)

  override fun <Res> workflowHandle(
      workflowName: String,
      workflowId: String,
      resTypeTag: TypeTag<Res>
  ): Client.WorkflowHandle<Res> = delegate.workflowHandle(workflowName, workflowId, resTypeTag)

  private fun formatRequest(request: Request<*, *>): String = buildString {
    append(request.target)
    if (request.idempotencyKey != null) append(" idempotency-key=${request.idempotencyKey}")
    val headers = request.headers
    if (!headers.isNullOrEmpty()) append(" headers=$headers")
    append("\n   payload: ")
    append(formatBody(request.request))
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Suppress("UNCHECKED_CAST")
  private fun formatBody(value: Any?): String {
    if (value == null || value == Unit) return "(empty)"
    if (value is ByteArray) return "[${value.size} bytes]"
    return try {
      val serializer =
          serializerOrNull(value.javaClass) as? KSerializer<Any> ?: return value.toString()
      JSON.encodeToString(serializer, value)
    } catch (_: Exception) {
      value.toString()
    }
  }
}
