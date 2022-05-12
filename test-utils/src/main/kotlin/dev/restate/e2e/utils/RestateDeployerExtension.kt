package dev.restate.e2e.utils

import io.grpc.Channel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.AbstractBlockingStub
import org.junit.jupiter.api.extension.*

class RestateDeployerExtension(private val deployer: RestateDeployer) : BeforeAllCallback, AfterAllCallback, ParameterResolver {

    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class InjectBlockingStub(val functionName: String)

    override fun beforeAll(context: ExtensionContext?) {
        deployer.deploy()
    }

    override fun afterAll(context: ExtensionContext?) {
        deployer.teardown()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.isAnnotated(InjectBlockingStub::class.java) &&
                parameterContext.parameter.type.isAssignableFrom(AbstractBlockingStub::class.java)
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        val functionName = parameterContext.findAnnotation(InjectBlockingStub::class.java).get().functionName
        val stubType = parameterContext.parameter.type
        val stubFactoryMethod = stubType.getDeclaredMethod("newBlockingStub", Channel::class.java)
        return stubFactoryMethod.invoke(
            null,
            deployer.getRuntimeFunctionEndpointUrl(functionName)
                .let { url -> NettyChannelBuilder.forAddress(url.host, url.port) })
    }
}