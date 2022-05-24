package dev.restate.e2e.gradle.util

fun hostArchitecture(): String {
    val currentArchitecture = org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentArchitecture()

    return if (currentArchitecture.isAmd64) {
        "amd64"
    } else {
        when (currentArchitecture.name) {
            "arm-v8", "aarch64", "arm64", "aarch_64" -> "arm64"
            else -> throw IllegalArgumentException("Not supported host architecture: $currentArchitecture")
        }
    }
}