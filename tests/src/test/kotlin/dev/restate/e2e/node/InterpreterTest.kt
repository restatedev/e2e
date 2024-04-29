// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.utils.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer

@Tag("always-suspending")
class InterpreterTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val deployerExt: RestateDeployerForEachExtension = RestateDeployerForEachExtension {
      RestateDeployer.Builder()
          .withInvokerRetryPolicy(RestateDeployer.RetryPolicy.FixedDelay("50ms", Int.MAX_VALUE))
          .withServiceEndpoint(
              Containers.nodeServicesContainer(
                      "intepreter",
                      "ObjectInterpreterL0",
                      "ObjectInterpreterL1",
                      "ObjectInterpreterL2",
                      "ServiceInterpreterHelper")
                  .withEnv("RESTATE_LOGGING", "ERROR"))
          .withContainer(
              "test-driver",
              GenericContainer("restatedev/e2e-node-services")
                  .withEnv("SERVICES", "InterpreterDriver")
                  .withEnv("InterpreterDriverPort", "3000")
                  .withExposedPorts(3000))
          .build()
    }

    private const val E2E_VERIFICATION_SEED_ENV = "E2E_VERIFICATION_SEED"

    private val ALPHANUMERIC_ALPHABET: Array<Char> =
        (('0'..'9').toList() + ('a'..'z').toList() + ('A'..'Z').toList()).toTypedArray()

    private val POLL_INTERVAL = 1.seconds.toJavaDuration()
    private val MAX_POLL_TIME = 45.seconds.toJavaDuration()

    private val LOG = LogManager.getLogger(InterpreterTest::class.java)

    fun generateAlphanumericString(length: Int): String {
      return List(length) { Random.nextInt(0, ALPHANUMERIC_ALPHABET.size) }
          .map { ALPHANUMERIC_ALPHABET[it] }
          .joinToString(separator = "")
    }
  }

  @Serializable
  data class InterpreterTestConfiguration(
      val ingress: String,
      val seed: String,
      val keys: Int,
      val tests: Int,
      val maxProgramSize: Int
  )

  @Serializable
  data class InterpreterStatusResponse(
      val status: String,
  )

  @Timeout(value = 1, unit = TimeUnit.MINUTES)
  @Test
  fun simple(@InjectContainerPort("test-driver", 3000) testDriverPort: Int) =
      runTest(timeout = 1.minutes) {
        val client = client()
        val httpResponse: HttpResponse =
            client.post("http://localhost:$testDriverPort/start") {
              contentType(ContentType.Application.Json)
              setBody(testParams())
            }
        assertThat(httpResponse.status).isEqualTo(HttpStatusCode.OK)

        await.pollInterval(POLL_INTERVAL).atMost(MAX_POLL_TIME) untilCallTo
            {
              runBlocking {
                client
                    .get("http://localhost:$testDriverPort/status")
                    .body<InterpreterStatusResponse>()
              }
            } matches
            { status ->
              status?.status.equals("finished", ignoreCase = true)
            }
      }

  private fun client(): HttpClient {
    return HttpClient(Java) { install(ContentNegotiation) { json() } }
  }

  private fun testParams(): InterpreterTestConfiguration {
    var testSeed = System.getenv(E2E_VERIFICATION_SEED_ENV)
    if (testSeed.isNullOrEmpty()) {
      testSeed = generateAlphanumericString(16)
    }
    LOG.info("Using seed {}", testSeed)

    return InterpreterTestConfiguration(
        "http://$RESTATE_RUNTIME:$RUNTIME_INGRESS_ENDPOINT_PORT/", testSeed, 10, 1, 100)
  }
}
