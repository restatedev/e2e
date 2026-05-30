# invoker-memory-kotlin

Containerized JVM counterpart of the in-process Kotlin services used by
`InvokerMemoryTest` (`MemoryPressureService` and `StatefulObject`).

Used by `InvokerMemoryTest` when `INVOKER_MEMORY_TEST_SDK=kotlin-container` is
set. The default `kotlin` mode still runs the same classes in-process; this
image runs them in a separate JVM so the comparison against `ts` isolates
"in-process vs out-of-process" without conflating SDK languages.

The service classes themselves live in the `:invoker-memory-kotlin-contracts`
module so both this image and the test JVM see a single source of truth.

## Drift hazard — read before publishing

The shadow JAR embeds whatever Restate Kotlin SDK is resolved at build time.
The test's in-process Kotlin path uses the same SDK from `mavenLocal()` (see
the root `build.gradle.kts` repositories block). If the in-process SDK is
updated, **this image must be republished**, otherwise `kotlin` vs
`kotlin-container` is no longer an apples-to-apples comparison of the same
SDK running in different processes.

## Publishing the multi-arch image to ghcr.io

```bash
# 1. Ensure the SDK you want is in mavenLocal. Typically by building the
#    instrumented SDK upstream (e.g. tillrohrmann/sdk-java@debug-sdk) with
#    `./gradlew publishToMavenLocal`.

# 2. Build the shadow JAR.
./gradlew :invoker-memory-kotlin-service:shadowJar

# 3. Build and push the multi-arch image. The JAR is JVM bytecode
#    (arch-independent); only the JRE base layer differs per arch.
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ghcr.io/restatedev/e2e-invoker-memory-kotlin:0.1.0 \
  --push \
  e2e-tests/services/invoker-memory-kotlin
```

Verify the resulting manifest contains both architectures:

```bash
docker manifest inspect ghcr.io/restatedev/e2e-invoker-memory-kotlin:0.1.0
```

Set the GHCR package visibility to **public** so workflow runners can pull
without authentication.

## Local development

```bash
./gradlew :invoker-memory-kotlin-service:shadowJar
java -jar e2e-tests/services/invoker-memory-kotlin/build/libs/invoker-memory-kotlin-service.jar
```

Then point a local Restate runtime at it (or use the test in debug mode).
