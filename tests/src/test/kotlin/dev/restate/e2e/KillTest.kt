package dev.restate.e2e

import dev.restate.e2e.Containers.FIXED_DELAY_RETRY_POLICY
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc
import dev.restate.e2e.functions.coordinator.CoordinatorGrpcKt
import dev.restate.e2e.functions.coordinator.CoordinatorProto
import dev.restate.e2e.functions.coordinator.duration
import dev.restate.e2e.utils.*
import dev.restate.e2e.utils.FunctionSpec.RegistrationOptions
import io.grpc.Channel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.InstanceOfAssertFactories
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest

// -- Simple sleep tests

@Tag("always-suspending")
class JavaKillTest : BaseKillTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
      RestateDeployerExtension(
        RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(Containers.JAVA_COORDINATOR_FUNCTION_SPEC)
          .build())
  }
}

@Tag("always-suspending")
class NodeKillTest : BaseKillTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
      RestateDeployerExtension(
        RestateDeployer.Builder()
          .withEnv(Containers.getRestateEnvironment())
          .withServiceEndpoint(Containers.NODE_COORDINATOR_FUNCTION_SPEC)
          .build())
  }
}

abstract class BaseKillTest {

  @Test
  fun sleepAndKill(@InjectChannel runtimeChannel: Channel, @InjectMetaURL metaUrl: URL) {
      val fut =
        CoordinatorGrpc.newFutureStub(runtimeChannel)
          .sleep(       duration { millis = 2.seconds.inWholeMilliseconds })

//    val client = HttpClient.newHttpClient()
//
//    val req =
//      HttpRequest.newBuilder(
//        URI.create(
//          "http://localhost:${getContainerPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT)}/endpoint/discover"))
//        .POST(HttpRequest.BodyPublishers.ofString(body))
//        .headers("Content-Type", "application/json")
//        .build()

      assertThatThrownBy { fut.get() }
        .asInstanceOf(type(StatusRuntimeException::class.java))
        .extracting(StatusRuntimeException::getStatus)
        .returns(Status.Code.ABORTED, Status::getCode)
  }
}