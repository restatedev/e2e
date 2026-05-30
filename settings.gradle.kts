plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

rootProject.name = "restate-e2e"

include(
    ":infra",
    ":e2e-tests",
    ":sdk-tests",
    ":invoker-memory-kotlin-contracts",
    ":invoker-memory-kotlin-service")

project(":invoker-memory-kotlin-contracts").projectDir =
    file("e2e-tests/services/invoker-memory-kotlin-contracts")
project(":invoker-memory-kotlin-service").projectDir =
    file("e2e-tests/services/invoker-memory-kotlin")
