package dev.restate.e2e.utils

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.AbstractBlockingStub
import io.grpc.stub.MetadataUtils
import org.junit.jupiter.api.extension.*

class RestateDeployerExtension(private val deployer: RestateDeployer) :
    BeforeAllCallback, AfterAllCallback, ParameterResolver {

  @Target(AnnotationTarget.VALUE_PARAMETER)
  annotation class InjectBlockingStub(val functionContainerName: String, val key: String = "")

  @Target(AnnotationTarget.VALUE_PARAMETER)
  annotation class InjectContainerAddress(val hostName: String, val port: Int)

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

  private fun resolveBlockingStub(parameterContext: ParameterContext): Any {
    val annotation = parameterContext.findAnnotation(InjectBlockingStub::class.java).get()

    val stubType = parameterContext.parameter.type
    val stubFactoryMethod =
        stubType.enclosingClass.getDeclaredMethod("newBlockingStub", Channel::class.java)
    var stub: AbstractBlockingStub<*> =
        stubFactoryMethod.invoke(
            null,
            deployer.getRuntimeFunctionEndpointUrl(annotation.functionContainerName).let { url ->
              NettyChannelBuilder.forAddress(url.host, url.port).usePlaintext().build()
            }) as AbstractBlockingStub<*>

    if (annotation.key != "") {
      val meta = Metadata()
      meta.put(Metadata.Key.of("x-restate-id", Metadata.ASCII_STRING_MARSHALLER), annotation.key)
      stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta))
    }

    return stub
  }

  private fun resolveContainerAddress(parameterContext: ParameterContext): Any {
    val annotation = parameterContext.findAnnotation(InjectContainerAddress::class.java).get()

    return deployer.getAdditionalContainerExposedPort(annotation.hostName, annotation.port)
  }
}
