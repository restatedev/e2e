package dev.restate.e2e.utils

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InjectContainerAddress(val hostName: String, val port: Int)
