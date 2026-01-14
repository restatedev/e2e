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
import dev.restate.admin.model.UpdateDeploymentRequest
import dev.restate.admin.model.UpdateHttpDeploymentRequest
import dev.restate.client.Client
import dev.restate.client.SendResponse
import dev.restate.client.kotlin.*
import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.endpoint.inactivityTimeout
import dev.restate.sdk.kotlin.endpoint.journalRetention
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
    suspend fun run(ctx: ObjectContext): String {
      val awk = ctx.awakeable<String>()
      ctx.set<String>("awk", awk.id)
      return awk.await()
    }

    @Shared
    suspend fun getAwakeable(ctx: SharedObjectContext): String = ctx.get<String>("awk") ?: ""
  }

  @Service
  @Name("RetryableService")
  interface RetryableService {
    @Handler suspend fun runRetryableOperation(ctx: Context): String
  }

  class FailingRetryableService : RetryableService {
    override suspend fun runRetryableOperation(ctx: Context): String {
      return ctx.runBlock {
        runBlockRetryCounter.incrementAndGet()
        throw RuntimeException("This should fail in old version")
      }
    }
  }

  class FixedRetryableService : RetryableService {
    override suspend fun runRetryableOperation(ctx: Context): String {
      return ctx.runBlock { "Success in new version!" }
    }
  }

  @Service
  @Name("ProxyService")
  class ProxyService {
    @Handler
    suspend fun proxy(ctx: Context): String {
      callRetryCounter.incrementAndGet()
      return ForwardCompatibilityTestCalleeServiceClient.fromContext(ctx).call().await()
    }

    @Handler
    suspend fun proxyOneWay(ctx: Context): String {
      oneWayCallRetryCounter.incrementAndGet()
      ForwardCompatibilityTestCalleeServiceClient.fromContext(ctx).send().call()
      return "Done"
    }
  }

  @Service
  @Name("CalleeService")
  class CalleeService {
    @Handler fun call(ctx: Context) = "Hello from callee!"
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
      val client = ForwardCompatibilityTestMyServiceClient.fromClient(ingressClient, awakeableKey)

      client.send().run(init = idempotentCallOptions)

      // Wait for awakeable to be registered
      await withAlias
          "awakeable is registered" untilAsserted
          {
            assertThat(client.getAwakeable()).isNotBlank()
          }
    }

    @Test
    fun startRetryableOperation(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ForwardCompatibilityTestRetryableServiceClient.fromClient(ingressClient)

      // Send the request and expect it to fail
      retryableClient.send().runRetryableOperation { idempotencyKey = idempotencyKeyRunBlockTest }

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
      val retryableClient = ForwardCompatibilityTestProxyServiceClient.fromClient(ingressClient)

      retryableClient.send().proxy { idempotencyKey = idempotencyKeyCallTest }

      // Wait for at least one retry
      await withAlias
          "operation was retried" untilAsserted
          {
            assertThat(callRetryCounter.get()).isGreaterThanOrEqualTo(2)
          }
    }

    @Test
    fun startOneWayProxyCall(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ForwardCompatibilityTestProxyServiceClient.fromClient(ingressClient)

      retryableClient.send().proxyOneWay { idempotencyKey = idempotencyKeyOneWayCallTest }

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
      val adminApi =
          DeploymentApi(
              ApiClient()
                  .setHost(adminURI.host)
                  // TODO remove basePath
                  .setBasePath("/v2")
                  .setPort(adminURI.port))

      // List all deployments
      val deployments = adminApi.listDeployments()

      LOG.info("Patching all deployments to use endpoint URI: {}", localEndpointURI)

      // For each deployment, update its URI
      for (deployment in deployments.deployments) {
        val updateRequest =
            UpdateDeploymentRequest(UpdateHttpDeploymentRequest().uri(localEndpointURI.toString()))

        try {
          adminApi.updateDeployment(deployment.httpDeploymentResponse.id, updateRequest)
          LOG.info(
              "Successfully updated deployment {} to use URI {}",
              deployment.httpDeploymentResponse.id,
              localEndpointURI)
        } catch (e: Exception) {
          LOG.error(
              "Failed to update deployment {}: {}", deployment.httpDeploymentResponse.id, e.message)
          throw e
        }
      }
    }

    @Test
    fun completeAwakeable(@InjectClient ingressClient: Client) = runTest {
      val client = ForwardCompatibilityTestMyServiceClient.fromClient(ingressClient, awakeableKey)

      val awakeableId = client.getAwakeable(idempotentCallOptions)
      assertThat(client.getAwakeable(idempotentCallOptions)).isNotBlank()

      val expectedResult = "solved!"
      await withAlias
          "resolve awakeable" untilAsserted
          {
            ingressClient.awakeableHandle(awakeableId).resolveSuspend(jsonSerde(), expectedResult)
          }
    }

    @Test
    fun completeRetryableOperation(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ForwardCompatibilityTestRetryableServiceClient.fromClient(ingressClient)

      val result =
          retryableClient.send().runRetryableOperation {
            idempotencyKey = idempotencyKeyRunBlockTest
          }

      assertThat(result.sendStatus).isEqualTo(SendResponse.SendStatus.PREVIOUSLY_ACCEPTED)

      // The operation should now complete successfully with the fixed implementation
      assertThat(result.attachSuspend().response).isEqualTo("Success in new version!")
    }

    @Test
    fun proxyCallShouldBeDone(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ForwardCompatibilityTestProxyServiceClient.fromClient(ingressClient)

      val result = retryableClient.send().proxy { idempotencyKey = idempotencyKeyCallTest }

      assertThat(result.sendStatus).isEqualTo(SendResponse.SendStatus.PREVIOUSLY_ACCEPTED)

      // The operation should now complete successfully with the fixed implementation
      assertThat(result.attachSuspend().response).isEqualTo("Hello from callee!")
    }

    @Test
    fun proxyOneWayCallShouldBeDone(@InjectClient ingressClient: Client) = runTest {
      val retryableClient = ForwardCompatibilityTestProxyServiceClient.fromClient(ingressClient)

      val result =
          retryableClient.send().proxyOneWay { idempotencyKey = idempotencyKeyOneWayCallTest }

      assertThat(result.sendStatus).isEqualTo(SendResponse.SendStatus.PREVIOUSLY_ACCEPTED)

      // The operation should now complete successfully with the fixed implementation
      assertThat(result.attachSuspend().response).isEqualTo("Done")
    }
  }
}
