package dev.restate.e2e.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.google.protobuf.Descriptors
import dev.restate.generated.ext.Ext
import dev.restate.generated.ext.ServiceType
import io.grpc.ServiceDescriptor
import io.grpc.protobuf.ProtoServiceDescriptorSupplier
import java.net.URL
import org.testcontainers.containers.GenericContainer
import org.testcontainers.lifecycle.Startable
import org.testcontainers.utility.DockerImageName

/** Definition of a function to deploy. */
data class FunctionSpec(
    internal val containerImage: String,
    internal val hostName: String,
    internal val services: List<ServiceDescriptor>,
    internal val envs: Map<String, String>,
    internal val grpcEndpointPort: Int,
    internal val dependencies: List<Startable>,
) {

  companion object {
    @JvmStatic
    fun builder(
        containerImage: String,
        grpcService: ServiceDescriptor,
        vararg otherGrpcServices: ServiceDescriptor
    ): Builder {
      return Builder(containerImage, mutableListOf(grpcService).apply { addAll(otherGrpcServices) })
    }
  }

  init {
    assert(services.isNotEmpty()) { "FunctionSpec must contain at least one service" }
  }

  data class Builder(
      private var containerImage: String,
      private var services: MutableList<ServiceDescriptor>,
      private var hostName: String =
          containerImage
              .trim()
              .split(Regex.fromLiteral("/"))
              .last()
              .split(Regex.fromLiteral(":"))
              .first(),
      private var envs: MutableMap<String, String> = mutableMapOf(),
      private var grpcEndpointPort: Int = 8080,
      private var dependencies: MutableList<Startable> = mutableListOf(),
  ) {
    fun withContainerImage(containerImage: String) = apply { this.containerImage = containerImage }

    fun withHostName(containerImage: String) = apply { this.containerImage = containerImage }

    fun withServices(vararg services: ServiceDescriptor) = apply { this.services.addAll(services) }

    fun withEnv(key: String, value: String) = apply { this.envs[key] = value }

    fun withGrpcEndpointPort(grpcEndpointPort: Int) = apply {
      this.grpcEndpointPort = grpcEndpointPort
    }

    fun withEnvs(envs: Map<String, String>) = apply { this.envs.putAll(envs) }

    fun dependsOn(container: Startable) = apply { this.dependencies.add(container) }

    fun build() =
        FunctionSpec(containerImage, hostName, services, envs, grpcEndpointPort, dependencies)
  }

  internal fun toFunctionContainer(): GenericContainer<*> {
    return GenericContainer(DockerImageName.parse(containerImage))
        .withEnv("PORT", grpcEndpointPort.toString())
        .withEnv(envs)
        .dependsOn(dependencies)
        .withExposedPorts(grpcEndpointPort)
  }

  internal fun toManifests(mapper: ObjectMapper): List<JsonNode> {
    return this.services.map { toManifest(mapper, it) }
  }

  private fun toManifest(mapper: ObjectMapper, serviceDescriptor: ServiceDescriptor): JsonNode {
    val protoServiceDescriptor =
        (serviceDescriptor.schemaDescriptor as ProtoServiceDescriptorSupplier).serviceDescriptor
    check(protoServiceDescriptor.options.hasExtension(Ext.serviceType)) {
      "The service ${protoServiceDescriptor.fullName} does not contain the dev.restate.ext.service_type extension. This is required to identify the service type."
    }
    val serviceInstanceType = protoServiceDescriptor.options.getExtension(Ext.serviceType)

    val manifestNode = mapper.createObjectNode()

    manifestNode.put("name", serviceDescriptor.name)
    manifestNode.put("endpoint", getFunctionEndpointUrl().toString())
    manifestNode.put(
        "service_instance_type",
        if (serviceInstanceType == ServiceType.KEYED) "Keyed" else "Unkeyed")

    val methodsNode = mapper.createArrayNode()
    protoServiceDescriptor.methods
        .map { toMethod(mapper, it, serviceInstanceType) }
        .forEach { methodsNode.add(it) }

    manifestNode.set<JsonNode>("methods", methodsNode)

    return manifestNode
  }

  private fun toMethod(
      mapper: ObjectMapper,
      methodDescriptor: Descriptors.MethodDescriptor,
      serviceInstanceType: ServiceType
  ): JsonNode {
    val methodNode = mapper.createObjectNode()
    methodNode.put("name", methodDescriptor.name)

    if (serviceInstanceType == ServiceType.KEYED) {
      val inputDescriptor = methodDescriptor.inputType

      // Look out for the key
      val keyField = inputDescriptor.fields.find { it.options.hasExtension(Ext.field) }!!
      val keyNode = mapper.createObjectNode()
      keyNode.put("root_message_key_field_number", keyField.number)
      keyNode.set<JsonNode>("parser_directive", toKeyParserDirective(mapper, keyField))

      methodNode.set<JsonNode>("key", keyNode)
    }

    return methodNode
  }

  private fun toKeyParserDirective(
      mapper: ObjectMapper,
      keyField: Descriptors.FieldDescriptor
  ): JsonNode {
    return if (keyField.type.javaType == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
      val directiveNode = mapper.createObjectNode()
      val recurseNode = mapper.createObjectNode()

      keyField.messageType.fields.forEach {
        recurseNode.set<JsonNode>(it.number.toString(), toKeyParserDirective(mapper, it))
      }

      directiveNode.set<JsonNode>("Recurse", recurseNode)
      directiveNode
    } else {
      TextNode("Stop")
    }
  }

  internal fun getFunctionEndpointUrl(): URL {
    return URL("http", this.hostName, this.grpcEndpointPort, "/")
  }
}
