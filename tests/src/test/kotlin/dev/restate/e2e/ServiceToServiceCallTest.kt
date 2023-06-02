package dev.restate.e2e

import com.google.protobuf.Empty
import dev.restate.e2e.Containers.javaServicesContainer
import dev.restate.e2e.Containers.nodeServicesContainer
import dev.restate.e2e.services.coordinator.CoordinatorGrpc
import dev.restate.e2e.services.receiver.ReceiverGrpc
import dev.restate.e2e.utils.InjectBlockingStub
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Tag("always-suspending")
class JavaServiceToServiceCallTest : BaseServiceToServiceCallTest() {
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
class NodeServiceToServiceCallTest : BaseServiceToServiceCallTest() {
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

@Tag("always-suspending")
class JavaCoordinatorWithNodeReceiverServiceToServiceCallTest : BaseServiceToServiceCallTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    javaServicesContainer("java-coordinator", CoordinatorGrpc.SERVICE_NAME))
                .withServiceEndpoint(
                    nodeServicesContainer("node-coordinator", ReceiverGrpc.SERVICE_NAME))
                .build())
  }
}

@Tag("always-suspending")
class NodeCoordinatorWithJavaReceiverServiceToServiceCallTest : BaseServiceToServiceCallTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withEnv(Containers.getRestateEnvironment())
                .withServiceEndpoint(
                    nodeServicesContainer("node-coordinator", CoordinatorGrpc.SERVICE_NAME))
                .withServiceEndpoint(
                    javaServicesContainer("java-coordinator", ReceiverGrpc.SERVICE_NAME))
                .build())
  }
}

abstract class BaseServiceToServiceCallTest {

  @Test
  fun synchronousCall(
      @InjectBlockingStub coordinatorClient: CoordinatorGrpc.CoordinatorBlockingStub
  ) {
    val response = coordinatorClient.proxy(Empty.getDefaultInstance())

    assertThat(response.message).isEqualTo("pong")
  }
}
