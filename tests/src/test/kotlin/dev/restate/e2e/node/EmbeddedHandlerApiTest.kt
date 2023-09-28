package dev.restate.e2e.node

import dev.restate.e2e.Containers
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_CONTAINER_SPEC
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_HOSTNAME
import dev.restate.e2e.Containers.EMBEDDED_HANDLER_SERVER_PORT
import dev.restate.e2e.utils.InjectContainerPort
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/** Test the Embedded handler API */
class EmbeddedHandlerApiTest {

  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withContainer(EMBEDDED_HANDLER_SERVER_CONTAINER_SPEC)
                .build())
  }

  // TODO this test is an example, you can remove it
  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun example(
      @InjectContainerPort(
          hostName = EMBEDDED_HANDLER_SERVER_HOSTNAME, port = EMBEDDED_HANDLER_SERVER_PORT)
      embeddedHandlerServerPort: Int
  ) {
    val client = HttpClient.newHttpClient()

    val req =
        HttpRequest.newBuilder(URI.create("http://localhost:${embeddedHandlerServerPort}/")).build()

    val response = client.send(req, BodyHandlers.ofString())

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).isEqualTo("Hello Restate http://runtime:9090/")
  }
}
