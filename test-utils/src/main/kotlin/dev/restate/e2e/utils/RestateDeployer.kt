package dev.restate.e2e.utils

import org.junit.jupiter.api.fail
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File
import java.net.URL
import java.nio.file.Files

class RestateDeployer private constructor(functions: List<FunctionContainer>) {

    companion object {
        private const val RUNTIME_CONTAINER = "ghcr.io/restatedev/runtime:main"
        private const val RUNTIME_GRPC_ENTRYPOINT = 8090

        private val logger = LoggerFactory.getLogger(RestateDeployer::class.java)
    }

    private val functionContainers = functions.associate { fn -> fn.name to fn.toGenericContainer() }
    private var runtimeContainer: GenericContainer<*>? = null

    init {
        if (functionContainers.size != 1) {
            fail("At the moment you MUST specify exactly one function container")
        }
    }

    data class FunctionContainer(val name: String) {
        internal fun toGenericContainer(): GenericContainer<*> {
            return GenericContainer(
                DockerImageName.parse("localhost/restatedev/$name")
            )
                .withExposedPorts(8080)
                .withEnv("PORT", "8080")
                .withLogConsumer(Slf4jLogConsumer(logger))
        }
    }

    data class Builder(var functions: MutableList<FunctionContainer> = mutableListOf()) {

        fun function(name: String) = apply { this.functions.add(FunctionContainer(name)) }

        fun function(functionContainer: FunctionContainer) = apply { this.functions.add(functionContainer) }

        fun build() = RestateDeployer(functions)
    }

    fun deploy() {
        // Deploy functions
        functionContainers.forEach { (name, genericContainer) ->
            genericContainer.start()
            logger.debug("Started function container {} with endpoint {}", name, getFunctionEndpointUrl(name))
        }

        // Generate config and write to temp dir
        val config = """
        |peers:
        |  - 127.0.0.1:9001
        |
        |grpc_port: $RUNTIME_GRPC_ENTRYPOINT
        |
        |consensus:
        |  storage_type: Memory
        |  
        |function_endpoint: ${getFunctionEndpointUrl(functionContainers.keys.first())}
        """.trimMargin("|")

        val configFile = File(Files.createTempDirectory("restate-e2e").toFile(), "restate.yaml").toPath()
        Files.writeString(configFile, config)
        logger.debug("Written config to {}", configFile)

        // Generate runtime container

        runtimeContainer = GenericContainer(
            DockerImageName.parse(RUNTIME_CONTAINER)
        )
            .dependsOn(functionContainers.values)
            .withEnv("RUST_LOG", "debug")
            .withExposedPorts(RUNTIME_GRPC_ENTRYPOINT)
            .withLogConsumer(Slf4jLogConsumer(logger))
            .withCopyFileToContainer(MountableFile.forHostPath(configFile), "/restate.yaml")
            .withCommand("--id 1 --configuration-file /restate.yaml")

        runtimeContainer!!.start()
    }

    fun teardown() {
        runtimeContainer!!.stop()
        functionContainers.forEach { (_, container) -> container.stop() }
    }

    fun getFunctionEndpointUrl(name: String): URL {
        val funcContainer =
            functionContainers[name] ?: throw java.lang.IllegalStateException("Function does not exists")
        return URL("http", "127.0.0.1", funcContainer.getMappedPort(8080), "/")
    }

    fun getRuntimeFunctionEndpointUrl(name: String): URL {
        return runtimeContainer?.getMappedPort(RUNTIME_GRPC_ENTRYPOINT)?.let {
            URL("http", "127.0.0.1", it, "/")
        }
            ?: throw java.lang.IllegalStateException("Runtime is not configured, as RestateDeployer::deploy has not been invoked")
    }

}