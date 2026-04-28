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
import dev.restate.client.Client
import dev.restate.client.SendResponse
import dev.restate.client.kotlin.*
import dev.restate.client.kotlin.response
import dev.restate.client.kotlin.toService
import dev.restate.client.kotlin.toVirtualObject
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdk.kotlin.endpoint.inactivityTimeout
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdk.kotlin.get
import dev.restate.sdk.kotlin.runBlock
import dev.restate.sdk.kotlin.set
import dev.restate.sdk.kotlin.state
import dev.restate.sdktesting.infra.Deployer
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.InjectClient
import dev.restate.sdktesting.infra.InjectLocalEndpointURI
import dev.restate.sdktesting.infra.RestateDeployer
import dev.restate.sdktesting.infra.RestateDeployerExtension
import dev.restate.serde.kotlinx.jsonSerde
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated

/** Tests to verify forward compatibility (older version can read data written by newer version). */
@Tag("version-compatibility")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@TestClassOrder(ClassOrderer.OrderAnnotation::class)
class ForwardCompatibilityTest {

  @VirtualObject
  class MyService {
    @Handler
    suspend fun run(): String {
      val awk = awakeable<String>()
      state().set("awk", awk.id)
      return awk.await()
    }

    @Shared suspend fun getAwakeable(): String = state().get<String>("awk") ?: ""
  }

  @Service
  @Name("RetryableService")
  interface RetryableService {
    @Handler suspend fun runRetryableOperation(): String
  }

  class FailingRetryableService : RetryableService {
    override suspend fun runRetryableOperation(): String {
      return runBlock {
        runBlockRetryCounter.incrementAndGet()
        throw RuntimeException("This should fail in old version")
      }
    }
  }

  class FixedRetryableService : RetryableService {
    override suspend fun runRetryableOperation(): String {
      return runBlock { "Success in new version!" }
    }
  }

  @Service
  @Name("ProxyService")
  class ProxyService {
    @Handler
    suspend fun proxy(): String {
      callRetryCounter.incrementAndGet()
      return dev.restate.sdk.kotlin.toService<CalleeService>().request { call() }.call().await()
    }

    @Handler
    suspend fun proxyOneWay(): String {
      oneWayCallRetryCounter.incrementAndGet()
      dev.restate.sdk.kotlin.toService<CalleeService>().request { call() }.send()
      return "Done"
    }
  }

  @Service
  @Name("CalleeService")
  class CalleeService {
    @Handler suspend fun call() = "Hello from callee!"
  }

  companion object {
    private val LOG = LogManager.getLogger(ForwardCompatibilityTest::class.java)
    private val stateDir = Files.createTempDirectory("forward-compatibility-test").toAbsolutePath()

    private val awakeableKey = UUID.randomUUID().toString()

    private val idempotencyKeyRunBlockTest = UUID.randomUUID().toString()
    private val runBlockRetryCounter = AtomicInteger(0)

    private val idempotencyKeyCallTest = UUID.randomUUID().toString()
    private val callRetryCounter = AtomicInteger(0)
    private val idempotencyKeyOneWayCallTest = UUID.randomUUID().toString()
    private val oneWayCallRetryCounter = AtomicInteger(0)

    init {
      LOG.info("Using state directory for forward compatibility test: {}", stateDir)
    }
  }

  @Tag("version-compatibility")
  @Nested
  @Order(1)
  @Isolated
  @Execution(ExecutionMode.SAME_THREAD)
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @ExtendWith(RestateDeployerExtension::class)
  inner class NewVersion {

    @Deployer
    val deployerExt: RestateDeployer.Builder.() -> Unit = {
      withEnv("RESTATE_CLUSTER_NAME", "forward-compatibility-test")
      withOverrideRestateStateDirectoryMount(stateDir.toString())
      withEndpoint(
          Endpoint.bind(MyService())
              .bind(FailingRetryableService())
              .bind(
                  ProxyService(),
                  {
                    it.journalRetention = 10.minutes
                    it.inactivityTimeout = 2.minutes
                  }))
    }

    @Test
    fun createAwakeable(@InjectClient ingressClient: Client) = runTest {
      val client = ingressClient.toVirtualObject<MyService>(awakeableKey)

      client.request { run() }.options(idempotentCallOptions).send()

      // Wait for awakeable to be registered
      await withAlias
          "awakeable is registered" untilAsserted
          {
            assertThat(client.request { getAwakeable() }.call().response).isNotBlank()
          }
    }

    @Test
    fun startRetryableOperation(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ingressClient.toService<RetryableService>()

      // Send the request and expect it to fail
      retryableClient
          .request { runRetryableOperation() }
          .options { idempotencyKey = idempotencyKeyRunBlockTest }
          .send()

      // Wait for at least one retry
      await withAlias
          "operation was retried" untilAsserted
          {
            assertThat(runBlockRetryCounter.get()).isGreaterThanOrEqualTo(1)
          }

      LOG.info("Operation was retried {} times", runBlockRetryCounter.get())
    }

    @Test
    fun startProxyCall(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ingressClient.toService<ProxyService>()

      retryableClient.request { proxy() }.options { idempotencyKey = idempotencyKeyCallTest }.send()

      // Wait for at least one retry
      await withAlias
          "operation was retried" untilAsserted
          {
            assertThat(callRetryCounter.get()).isGreaterThanOrEqualTo(2)
          }
    }

    @Test
    fun startOneWayProxyCall(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ingressClient.toService<ProxyService>()

      retryableClient
          .request { proxyOneWay() }
          .options { idempotencyKey = idempotencyKeyOneWayCallTest }
          .send()

      // Wait for at least one retry
      await withAlias
          "operation was retried" untilAsserted
          {
            assertThat(oneWayCallRetryCounter.get()).isGreaterThanOrEqualTo(2)
          }
    }
  }

  @Tag("version-compatibility")
  @Nested
  @Order(2)
  @Isolated
  @Execution(ExecutionMode.SAME_THREAD)
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @ExtendWith(RestateDeployerExtension::class)
  inner class OldVersion {

    @Deployer
    val deployerExt: RestateDeployer.Builder.() -> Unit = {
      withEnv("RESTATE_CLUSTER_NAME", "forward-compatibility-test")
      withOverrideRestateContainerImage(
          "ghcr.io/restatedev/restate:${Constants.LAST_COMPATIBLE_RESTATE_SERVER_VERSION}")
      withOverrideRestateStateDirectoryMount(stateDir.toString())
      withEndpoint(
          Endpoint.bind(MyService())
              .bind(FixedRetryableService())
              .bind(
                  ProxyService(),
                  {
                    it.journalRetention = 10.minutes
                    it.inactivityTimeout = 2.minutes
                  })
              .bind(
                  CalleeService(),
                  {
                    it.journalRetention = 20.minutes
                    it.inactivityTimeout = 2.minutes
                  }))
    }

    // We need to patch the service deployments, otherwise restate will continue retrying to the old
    // deployments
    @BeforeAll
    fun patchServiceDeployments(
        @InjectAdminURI adminURI: URI,
        @InjectLocalEndpointURI localEndpointURI: URI
    ) {
      // Create Admin API client with the provided admin URI
      val adminClient =
          ApiClient()
              .setHost(adminURI.host)
              .setPort(adminURI.port)
      val adminApi = DeploymentApi(adminClient)

      // List all deployments
      val deployments = adminApi.listDeployments()

      LOG.info("Patching all deployments to use endpoint URI: {}", localEndpointURI)

      // For each deployment, update its URI.
      // NOTE: We use a raw PUT request here instead of adminApi.updateDeployment() because
      // the generated client uses PATCH (per the current OpenAPI spec), but this test runs
      // against an OLDER server version that only supports PUT on this endpoint.
      // When the minimum supported version includes PATCH support, replace this block with:
      //   adminApi.updateDeployment(deployment.httpDeploymentResponse.id, updateRequest)
      val httpClient = java.net.http.HttpClient.newHttpClient()
      for (deployment in deployments.deployments) {
        val deploymentId = deployment.httpDeploymentResponse.id
        val body = """{"uri":"$localEndpointURI"}"""
        val request =
            java.net.http.HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "http://${adminURI.host}:${adminURI.port}/v2/deployments/$deploymentId"))
                .header("Content-Type", "application/json")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build()

        try {
          val response =
              httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
          check(response.statusCode() / 100 == 2) {
            "updateDeployment call failed with: ${response.statusCode()} - ${response.body()}"
          }
          LOG.info(
              "Successfully updated deployment {} to use URI {}", deploymentId, localEndpointURI)
        } catch (e: Exception) {
          LOG.error("Failed to update deployment {}: {}", deploymentId, e.message)
          throw e
        }
      }
    }

    @Test
    fun completeAwakeable(@InjectClient ingressClient: Client) = runTest {
      val client = ingressClient.toVirtualObject<MyService>(awakeableKey)

      val awakeableId =
          client.request { getAwakeable() }.options(idempotentCallOptions).call().response
      assertThat(client.request { getAwakeable() }.options(idempotentCallOptions).call().response)
          .isNotBlank()

      val expectedResult = "solved!"
      await withAlias
          "resolve awakeable" untilAsserted
          {
            ingressClient.awakeableHandle(awakeableId).resolveSuspend(jsonSerde(), expectedResult)
          }
    }

    @Test
    fun completeRetryableOperation(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ingressClient.toService<RetryableService>()

      val result =
          retryableClient
              .request { runRetryableOperation() }
              .options { idempotencyKey = idempotencyKeyRunBlockTest }
              .send()

      assertThat(result.sendStatus).isEqualTo(SendResponse.SendStatus.PREVIOUSLY_ACCEPTED)

      // The operation should now complete successfully with the fixed implementation
      assertThat(result.attachSuspend().response).isEqualTo("Success in new version!")
    }

    @Test
    fun proxyCallShouldBeDone(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ingressClient.toService<ProxyService>()

      val result =
          retryableClient
              .request { proxy() }
              .options { idempotencyKey = idempotencyKeyCallTest }
              .send()

      assertThat(result.sendStatus).isEqualTo(SendResponse.SendStatus.PREVIOUSLY_ACCEPTED)

      // The operation should now complete successfully with the fixed implementation
      assertThat(result.attachSuspend().response).isEqualTo("Hello from callee!")
    }

    @Test
    fun proxyOneWayCallShouldBeDone(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ingressClient.toService<ProxyService>()

      val result =
          retryableClient
              .request { proxyOneWay() }
              .options { idempotencyKey = idempotencyKeyOneWayCallTest }
              .send()

      assertThat(result.sendStatus).isEqualTo(SendResponse.SendStatus.PREVIOUSLY_ACCEPTED)

      // The operation should now complete successfully with the fixed implementation
      assertThat(result.attachSuspend().response).isEqualTo("Done")
    }
  }
}
