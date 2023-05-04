package dev.restate.e2e.utils

const val RESTATE_RUNTIME = "runtime"
internal const val RUNTIME_GRPC_INGRESS_ENDPOINT_PORT = 9090
internal const val RUNTIME_META_ENDPOINT_PORT = 8081

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectBlockingStub

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InjectContainerHandle(val hostName: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InjectContainerPort(val hostName: String, val port: Int)

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectGrpcIngressURL

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectMetaURL
