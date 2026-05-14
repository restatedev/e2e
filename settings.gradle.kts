plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

rootProject.name = "restate-e2e"

include(":infra", ":e2e-tests", ":sdk-tests")
