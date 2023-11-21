// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.utils

const val RESTATE_RUNTIME = "runtime"
internal const val RUNTIME_GRPC_INGRESS_ENDPOINT_PORT = 8080
internal const val RUNTIME_META_ENDPOINT_PORT = 9070

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectBlockingStub

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectChannel

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InjectContainerHandle(val hostName: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InjectContainerPort(val hostName: String, val port: Int)

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectGrpcIngressURL

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class InjectMetaURL
