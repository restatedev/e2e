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

abstract class BaseRestateDeployerExtension : ParameterResolver {

  companion object {
    internal val NAMESPACE = Namespace.create(BaseRestateDeployerExtension::class.java)
    private const val MANAGED_CHANNEL_KEY = "ManagedChannelKey"
    internal const val DEPLOYER_KEY = "Deployer"
  }

  override fun supportsParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Boolean {
    return (parameterContext.isAnnotated(InjectBlockingStub::class.java) &&
        AbstractBlockingStub::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectContainerPort::class.java) &&
            Int::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectGrpcIngressURL::class.java) &&
            URL::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectMetaURL::class.java) &&
            URL::class.java.isAssignableFrom(parameterContext.parameter.type)) ||
        (parameterContext.isAnnotated(InjectContainerHandle::class.java) &&
            ContainerHandle::class.java.isAssignableFrom(parameterContext.parameter.type))
  }

  override fun resolveParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Any? {
    return if (parameterContext.isAnnotated(InjectBlockingStub::class.java)) {
      resolveBlockingStub(parameterContext, extensionContext)
    } else if (parameterContext.isAnnotated(InjectContainerPort::class.java)) {
      resolveContainerAddress(parameterContext, extensionContext)
    } else if (parameterContext.isAnnotated(InjectGrpcIngressURL::class.java)) {
      resolveGrpcIngressURL(extensionContext)
    } else if (parameterContext.isAnnotated(InjectMetaURL::class.java)) {
      resolveMetaURL(extensionContext)
    } else if (parameterContext.isAnnotated(InjectContainerHandle::class.java)) {
      resolveContainerHandle(parameterContext, extensionContext)
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
                { ManagedChannelResource(getDeployer(extensionContext).createRuntimeChannel()) },
                ManagedChannelResource::class.java)

    return stubFactoryMethod.invoke(null, channelResource.channel) as T
  }

  private fun resolveContainerAddress(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Any {
    val annotation = parameterContext.findAnnotation(InjectContainerPort::class.java).get()

    return getDeployer(extensionContext).getContainerPort(annotation.hostName, annotation.port)
  }

  private fun resolveGrpcIngressURL(extensionContext: ExtensionContext): Any {
    return URL(
        "http",
        "127.0.0.1",
        getDeployer(extensionContext)
            .getContainerPort(RESTATE_RUNTIME, RUNTIME_GRPC_INGRESS_ENDPOINT_PORT),
        "/")
  }

  private fun resolveMetaURL(extensionContext: ExtensionContext): Any {
    return URL(
        "http",
        "127.0.0.1",
        getDeployer(extensionContext).getContainerPort(RESTATE_RUNTIME, RUNTIME_META_ENDPOINT_PORT),
        "/")
  }

  private fun resolveContainerHandle(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext
  ): Any {
    val annotation = parameterContext.findAnnotation(InjectContainerHandle::class.java).get()

    return getDeployer(extensionContext).getContainerHandle(annotation.hostName)
  }

  private fun getDeployer(extensionContext: ExtensionContext): RestateDeployer {
    return (extensionContext.getStore(NAMESPACE).get(DEPLOYER_KEY) as RestateDeployer?)!!
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
