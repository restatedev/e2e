package dev.restate.e2e.utils

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.AbstractBlockingStub
import io.grpc.stub.AbstractStub
import java.net.URL
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ExtensionContext.Store

class RestateDeployerExtension(private val deployer: RestateDeployer) :
    BeforeAllCallback, AfterAllCallback, ParameterResolver {

  companion object {
    private val NAMESPACE = Namespace.create(RestateDeployerExtension::class.java)
    private const val MANAGED_CHANNEL_KEY = "ManagedChannelKey"
  }

  override fun beforeAll(context: ExtensionContext) {
    deployer.deploy(context.requiredTestClass)
    context
        .getStore(NAMESPACE)
        .put(
            MANAGED_CHANNEL_KEY,
            ManagedChannelResource(
                deployer.getRuntimeGrpcEndpointUrl().let { url ->
                  NettyChannelBuilder.forAddress(url.host, url.port).usePlaintext().build()
                }))
  }

  override fun afterAll(context: ExtensionContext) {
    context
        .getStore(NAMESPACE)
        .get(MANAGED_CHANNEL_KEY, ManagedChannelResource::class.java)
        ?.close()
    deployer.teardown()
  }

  override fun supportsParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Boolean {
    return (parameterContext.isAnnotated(InjectBlockingStub::class.java) &&
        AbstractBlockingStub::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectContainerAddress::class.java) &&
            String::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectHttpEndpointURL::class.java) &&
            URL::class.java.isAssignableFrom(parameterContext.parameter.type))
  }

  override fun resolveParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Any? {
    return if (parameterContext.isAnnotated(InjectBlockingStub::class.java)) {
      resolveBlockingStub(parameterContext, extensionContext)
    } else if (parameterContext.isAnnotated(InjectContainerAddress::class.java)) {
      resolveContainerAddress(parameterContext)
    } else if (parameterContext.isAnnotated(InjectHttpEndpointURL::class.java)) {
      resolveHttpEndpointURL()
    } else {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : AbstractStub<T>> resolveBlockingStub(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Any {
    val stubType = parameterContext.parameter.type
    val stubFactoryMethod =
        stubType.enclosingClass.getDeclaredMethod("newBlockingStub", Channel::class.java)

    val channelResource =
        extensionContext
            .getStore(NAMESPACE)
            .get(MANAGED_CHANNEL_KEY, ManagedChannelResource::class.java)

    return stubFactoryMethod.invoke(null, channelResource.channel) as T
  }

  private fun resolveContainerAddress(parameterContext: ParameterContext): Any {
    val annotation = parameterContext.findAnnotation(InjectContainerAddress::class.java).get()

    return deployer.getAdditionalContainerExposedPort(annotation.hostName, annotation.port)
  }

  private fun resolveHttpEndpointURL(): Any {
    return deployer.getRuntimeHttpEndpointUrl()
  }

  private class ManagedChannelResource(val channel: ManagedChannel) : Store.CloseableResource {
    override fun close() {
      // Shutdown channel
      channel.shutdown()
      if (channel.awaitTermination(5, TimeUnit.SECONDS)) {
        return
      }

      // Force shutdown now
      channel.shutdownNow()
      check(!channel.awaitTermination(5, TimeUnit.SECONDS)) {
        "Cannot terminate ManagedChannel on time"
      }
    }
  }
}
