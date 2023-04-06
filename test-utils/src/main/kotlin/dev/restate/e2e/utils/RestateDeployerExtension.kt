package dev.restate.e2e.utils

import io.grpc.Channel
import io.grpc.ManagedChannel
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
    deployer.deployAll(context.requiredTestClass)
  }

  override fun afterAll(context: ExtensionContext) {
    deployer.teardownAll()
  }

  override fun supportsParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Boolean {
    return (parameterContext.isAnnotated(InjectBlockingStub::class.java) &&
        AbstractBlockingStub::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectContainerAddress::class.java) &&
            String::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectGrpcIngressURL::class.java) &&
            URL::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectMetaURL::class.java) &&
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
    } else if (parameterContext.isAnnotated(InjectGrpcIngressURL::class.java)) {
      resolveGrpcIngressURL()
    } else if (parameterContext.isAnnotated(InjectMetaURL::class.java)) {
      resolveMetaURL()
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
            .getOrComputeIfAbsent(
                MANAGED_CHANNEL_KEY,
                { ManagedChannelResource(deployer.getRuntimeChannel()) },
                ManagedChannelResource::class.java)

    return stubFactoryMethod.invoke(null, channelResource.channel) as T
  }

  private fun resolveContainerAddress(parameterContext: ParameterContext): Any {
    val annotation = parameterContext.findAnnotation(InjectContainerAddress::class.java).get()

    return deployer.getAdditionalContainerExposedPort(annotation.hostName, annotation.port)
  }

  private fun resolveGrpcIngressURL(): Any {
    return deployer.getRuntimeGrpcIngressUrl()
  }

  private fun resolveMetaURL(): Any {
    return deployer.getRuntimeMetaUrl()
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
