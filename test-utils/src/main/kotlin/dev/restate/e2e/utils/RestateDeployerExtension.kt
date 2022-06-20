package dev.restate.e2e.utils

import dev.restate.e2e.utils.GrpcUtils.withRestateKey
import io.grpc.Channel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.AbstractBlockingStub
import io.grpc.stub.AbstractStub
import org.junit.jupiter.api.extension.*

class RestateDeployerExtension(private val deployer: RestateDeployer) :
    BeforeAllCallback, AfterAllCallback, ParameterResolver {

  override fun beforeAll(context: ExtensionContext) {
    deployer.deploy(context.requiredTestClass)
  }

  override fun afterAll(context: ExtensionContext) {
    deployer.teardown()
  }

  override fun supportsParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Boolean {
    return (parameterContext.isAnnotated(InjectBlockingStub::class.java) &&
        AbstractBlockingStub::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectContainerAddress::class.java) &&
            String::class.java.isAssignableFrom(parameterContext.parameter.type))
  }

  override fun resolveParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Any? {
    return if (parameterContext.isAnnotated(InjectBlockingStub::class.java)) {
      resolveBlockingStub(parameterContext)
    } else if (parameterContext.isAnnotated(InjectContainerAddress::class.java)) {
      resolveContainerAddress(parameterContext)
    } else {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : AbstractStub<T>> resolveBlockingStub(parameterContext: ParameterContext): Any {
    val annotation = parameterContext.findAnnotation(InjectBlockingStub::class.java).get()

    val stubType = parameterContext.parameter.type
    val stubFactoryMethod =
        stubType.enclosingClass.getDeclaredMethod("newBlockingStub", Channel::class.java)
    var stub: T =
        stubFactoryMethod.invoke(
            null,
            deployer.getRuntimeFunctionEndpointUrl().let { url ->
              NettyChannelBuilder.forAddress(url.host, url.port).usePlaintext().build()
            }) as T

    if (annotation.key != "") {
      stub = stub.withRestateKey(annotation.key)
    }

    return stub
  }

  private fun resolveContainerAddress(parameterContext: ParameterContext): Any {
    val annotation = parameterContext.findAnnotation(InjectContainerAddress::class.java).get()

    return deployer.getAdditionalContainerExposedPort(annotation.hostName, annotation.port)
  }
}
