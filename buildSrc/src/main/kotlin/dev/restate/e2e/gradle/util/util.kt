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

fun testBaseImage(): String {
    return when (hostArchitecture()) {
        "arm64" -> "eclipse-temurin:17-jre@sha256:61c5fee7a5c40a1ca93231a11b8caf47775f33e3438c56bf3a1ea58b7df1ee1b"
        "amd64" -> "eclipse-temurin:17-jre@sha256:ff7a89fe868ba504b09f93e3080ad30a75bd3d4e4e7b3e037e91705f8c6994b3"
        else -> throw java.lang.IllegalArgumentException("No image for host architecture: ${hostArchitecture()}")
    }
}