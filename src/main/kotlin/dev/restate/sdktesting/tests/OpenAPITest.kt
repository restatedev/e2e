// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Name
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.kotlin.Context
import dev.restate.sdktesting.infra.InjectAdminURI
import dev.restate.sdktesting.infra.RestateDeployerExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.openapitools.codegen.DefaultGenerator
import org.openapitools.codegen.config.CodegenConfigurator

class OpenAPITest {

  @Service
  @Name("GreeterService")
  class GreeterService {
    @Handler
    suspend fun greet(ctx: Context, name: String): String {
      return "Hello, $name!"
    }
  }

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withEndpoint(Endpoint.bind(GreeterService()))
    }
  }

  @Test
  fun shouldGenerateValidOpenAPI(@InjectAdminURI adminURI: URI) = runTest {
    // Download OpenAPI spec from Admin API
    val httpClient = HttpClient.newHttpClient()
    val request =
        HttpRequest.newBuilder()
            .uri(URI.create("${adminURI}services/GreeterService/openapi"))
            .GET()
            .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(200)

    // Save OpenAPI spec to a temporary file
    val tempDir = Files.createTempDirectory("openapi-test")
    val openApiFile = tempDir.resolve("openapi.json")
    Files.writeString(openApiFile, response.body())

    // Configure and run OpenAPI Generator
    val configurator =
        CodegenConfigurator().apply {
          setInputSpec(openApiFile.toString())
          setOutputDir(tempDir.resolve("generated").toString())
          setGeneratorName("java")
          setApiPackage("dev.restate.generated.api")
          setModelPackage("dev.restate.generated.model")
          // Using the latest Java client generator features
          setAdditionalProperties(
              mapOf(
                  "dateLibrary" to "java8",
                  "artifactId" to "restate-generated-client",
                  "artifactVersion" to "1.0.0"))
        }

    // Generate the client code
    val generator = DefaultGenerator()
    val files = generator.opts(configurator.toClientOptInput()).generate()

    // Verify that files were generated
    assertThat(files).isNotEmpty()
    assertThat(tempDir.resolve("generated/src/main/java")).exists()

    // Verify specific generated files that should exist
    assertThat(tempDir.resolve("generated/src/main/java/dev/restate/generated/api")).exists()
    assertThat(tempDir.resolve("generated/src/main/java/dev/restate/generated/model")).exists()
  }
}
