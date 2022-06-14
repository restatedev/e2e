package dev.restate.e2e.utils

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class InjectBlockingStub(val functionContainerName: String, val key: String = "")
