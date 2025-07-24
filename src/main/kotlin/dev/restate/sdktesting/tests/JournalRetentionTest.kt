// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.client.Client
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.endpoint.*
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class JournalRetentionTest {

  @Service
  class MyService {

    @Handler
    suspend fun greet(ctx: Context, input: String): String {
      ctx.sleep(100.milliseconds)
      return input
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(MyService()) { it.journalRetention = 1.days })
    }
  }

  @Test
  fun journalShouldBeRetained(
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) = runTest {
    val client = JournalRetentionTestMyServiceClient.fromClient(ingressClient)
    val invocationId = client.send().greet("Francesco", init = idempotentCallOptions).invocationId()

    await withAlias
        "got the invocation completed, with the journal retained" untilAsserted
        {
          assertThat(getInvocationStatus(adminURI, invocationId))
              .isEqualTo(SysInvocationEntry(id = invocationId, status = "completed"))
          assertThat(getJournal(adminURI, invocationId).rows)
              .containsExactly(
                  SysJournalEntry(0, "Command: Input"),
                  SysJournalEntry(1, "Command: Sleep"),
                  SysJournalEntry(2, "Notification: Sleep"),
                  SysJournalEntry(3, "Command: Output"))
        }
  }
}
