// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.restate.sdk.annotation.*
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.endpoint.journalRetention
import dev.restate.sdktesting.infra.*
import dev.restate.sdktesting.tests.openapi.checkCompatibility
import dev.restate.sdktesting.tests.openapi.generateClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.RegisterExtension

class OpenAPITest {

  @Serializable data class Details(val language: String)

  @Serializable data class Greeting(val name: String, val details: Details)

  @Service
  @Name("GreeterService")
  class GreeterService {
    @Handler suspend fun greet(greeting: Greeting): Greeting = greeting
  }

  @VirtualObject
  @Name("GreeterObject")
  class GreeterObject {
    @Handler suspend fun greet(greeting: Greeting): Greeting = greeting

    @Shared suspend fun read(): String = "ready"
  }

  @Workflow
  @Name("GreeterWorkflow")
  class GreeterWorkflow {
    @Workflow suspend fun run(greeting: Greeting): Greeting = greeting

    @Shared suspend fun read(): String = "ready"
  }

  companion object {
    private val mapper = ObjectMapper()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private lateinit var artifacts: Path

    @RegisterExtension
    @JvmField
    val reports = BeforeAllCallback { context ->
      artifacts =
          Files.createDirectories(
              Path.of(
                      context
                          .getConfigurationParameter(
                              BaseRestateDeployerExtension.REPORT_DIR_PROPERTY_NAME
                          )
                          .orElse("test_report")
                  )
                  .resolve("OpenAPITest/openapi")
          )
    }

    @RegisterExtension
    @JvmField
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(
          Endpoint.bind(GreeterService()) { it.journalRetention = 1.days }
              .bind(GreeterObject())
              .bind(GreeterWorkflow())
      )
    }
  }

  @Test
  fun adminClientWorksAgainstCandidate(
      @InjectAdminURI adminURI: URI,
      @InjectIngressURI ingressURI: URI,
  ) {
    val spec = download(adminURI.resolve("openapi"), "admin.json")
    val invocationId = completedInvocation(ingressURI)
    generateClient(
            spec,
            artifacts.resolve("admin-client"),
            "dev.restate.candidate",
            "AdminSmoke.java",
        )
        .use { client ->
          // The Java smoke test is compiled together with the freshly generated client. This checks
          // generated method signatures as well as runtime serialization/deserialization.
          client
              .loadClass("AdminSmoke")
              .getMethod("run", URI::class.java, String::class.java)
              .invoke(null, adminURI, invocationId)
        }
  }

  @Test
  fun adminContractIsBackwardsCompatible(@InjectAdminURI adminURI: URI) {
    val candidate = download(adminURI.resolve("openapi"), "admin-compatibility.json")
    val baseline = artifacts.resolve("admin-baseline.json")
    checkNotNull(javaClass.getResourceAsStream("/openapi/baseline/admin.json")).use {
      Files.copy(it, baseline, StandardCopyOption.REPLACE_EXISTING)
    }
    checkCompatibility(baseline, candidate, artifacts.resolve("breaking-changes.txt"))
  }

  @Test
  fun serviceClientsWorkAgainstCandidate(
      @InjectAdminURI adminURI: URI,
      @InjectIngressURI ingressURI: URI,
  ) {
    for (service in listOf("GreeterService", "GreeterObject", "GreeterWorkflow")) {
      val spec = download(adminURI.resolve("services/$service/openapi"), "$service.json")
      generateClient(
              spec,
              artifacts.resolve("$service-client"),
              // Each client has its own classloader, so the common invocation smoke test can use
              // the same package while compiling independently against all three documents.
              "dev.restate.ingress",
              "${service}Smoke.java",
              "InvocationSmoke.java",
          )
          .use { client ->
            client
                .loadClass("${service}Smoke")
                .getMethod("run", URI::class.java)
                .invoke(null, ingressURI)
          }
    }
  }

  private fun completedInvocation(ingressURI: URI): String {
    val result =
        request(ingressURI.resolve("restate/send/GreeterService/greet"), "POST", payload(), 202)
    val id = result.path("invocationId").asText()
    assertThat(id).isNotBlank()
    request(ingressURI.resolve("restate/attach/$id"), "GET", null, 200)
    return id
  }

  private fun payload() = """{"name":"Ada","details":{"language":"en"}}"""

  private fun download(uri: URI, name: String): Path {
    val document = request(uri, "GET", null, 200)
    val path = artifacts.resolve(name)
    Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document))
    return path
  }

  private fun request(uri: URI, method: String, body: String?, status: Int): JsonNode {
    val request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("accept", "application/json")
            .apply { if (body != null) header("content-type", "application/json") }
            .method(
                method,
                body?.let { HttpRequest.BodyPublishers.ofString(it) }
                    ?: HttpRequest.BodyPublishers.noBody(),
            )
            .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode())
        .withFailMessage("%s %s: %s", method, uri, response.body())
        .isEqualTo(status)
    return mapper.readTree(response.body())
  }
}
