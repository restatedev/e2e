# Project Guidelines (Advanced)

This document captures project-specific knowledge to speed up development and debugging.

## Build and Configuration

- JDK: The project requires Java 21+ (toolchain enforced by Gradle: `kotlin { jvmToolchain(21) }`). Verify with `./gradlew javaToolchains`.
- Build system: Gradle (Kotlin DSL). Key tasks:
  - `./gradlew build` — compiles sources, generates code (jsonschema2pojo + openapi), runs tests, formatting checks.
  - `./gradlew run` — launches the e2e test runner CLI (main class `dev.restate.sdktesting.MainKt`).
  - `./gradlew test` — executes JUnit tests (JUnit Platform).
- Code generation:
  - JSON Schema → POJOs via `jsonSchema2Pojo` from `src/main/json` into `build/generated/j2sp`.
  - OpenAPI → Admin API client from `src/main/openapi/admin.json` into `build/generated/openapi`.
  - KSP (Restate SDK API Kotlin codegen) runs after OpenAPI generation (`kspKotlin` must run after `openApiGenerate`). Gradle is already wired: KotlinCompile depends on both generate tasks.
- Application packaging: `application` plugin provides start scripts via `installDist` and `distZip` if you need a distributable CLI.
- Spotless formatting is configured with ktfmt and a project license header; run `./gradlew spotlessApply` before committing.
- Repositories: mavenCentral only by default; optional snapshot/local repos are commented in `build.gradle.kts`.

## Runtime and Environment

- The e2e harness spins up Restate runtimes (containers). All env vars prefixed with `RESTATE_` or `RUST_` are propagated to each runtime container (see README). Useful ones during compatibility testing:
  - `RESTATE_CLUSTER_NAME` — identify/runtime grouping for tests.
  - `RESTATE_DEFAULT_RETRY_POLICY__*` — tweak retry policies for pause/suspend scenarios.
- Images:
  - Use `--restate-container-image=<IMAGE>` to override the runtime image (CLI arg passed via `./gradlew run --args ...`).
  - Pull policy: default always pull except `restate.local/*` or `localhost/*`; override with `--image-pull-policy=CACHED` to prefer local cache.

## Running Tests

Two paths exist, pick based on what you’re changing:

1) JUnit directly via Gradle
- Command: `./gradlew test`
- The project uses JUnit 5 (including parallelism and `@Isolated` where needed). Test reports land under `build/reports/tests/test`.
- Use standard Gradle/JUnit filtering if needed, e.g. `./gradlew test --tests "dev.restate.sdktesting.tests.FrontCompatibilityTest*"`.

2) E2E CLI harness (preferred for end-to-end scenarios)
- Run all: `./gradlew run`
- Run specific suite/test:
  - `./gradlew run --args 'run --test-suite=<SUITE> --test-name=<TEST_NAME>'`
- Additional args: `--restate-container-image=...`, `--image-pull-policy=CACHED` as above.

Note: The CLI and JUnit both rely on generated sources (OpenAPI client and jsonschema2pojo). Gradle ensures these generators run before compile; you don't need manual steps.

## Adding and Executing New Tests

- Location: add new Kotlin test classes under `src/main/kotlin/dev/restate/sdktesting/tests` when they are part of the harness-run suites, or under `src/test` for classic Gradle test sources. Existing suites like `FrontCompatibilityTest` live under `main` because they are executed by the CLI harness and embed deployment logic.
- Annotations and Infra:
  - Use `@ExtendWith(RestateDeployerExtension::class)` to get the Restate cluster lifecycle and DI.
  - Dependency injection annotations (from infra):
    - `@Deployer` on a `RestateDeployer.Builder.() -> Unit` to declare runtime, env overrides, endpoints to bind.
    - `@InjectClient` to obtain a `dev.restate.client.Client` ingress client.
    - `@InjectAdminURI` to get the Admin API URI for direct interactions via generated `InvocationApi`.
  - Services: bind endpoints via `Endpoint.bind(...)` and set journal retention/inactivity using Kotlin DSL: e.g., `it.journalRetention = 5.minutes`.
  - Awaitility is used to synchronize on cluster state: `await withAlias "..." untilAsserted { ... }`.
- Idempotency and Retry patterns:
  - Prefer `idempotentCallOptions` and explicit `idempotencyKey` for operations that may be retried across version bumps.
  - Adjust `RESTATE_DEFAULT_RETRY_POLICY__*` to force pause/suspend paths when validating compatibility.
- Executing new tests:
  - Via Gradle/JUnit: filter by package or class name as usual (`--tests`).
  - Via CLI harness: pass `--test-suite` and `--test-name` as in README. Ensure your new suite is discoverable by the CLI (see TestSuites registry under `dev.restate.sdktesting.junit`).

## Debugging Tips

- Admin API:
  - Use the generated client (`dev.restate.admin.api.InvocationApi`) to list or resume invocations. Example (see FrontCompatibilityTest#NewVersion): `InvocationApi(ApiClient().setHost(adminURI.host).setPort(adminURI.port)).resumeInvocation(id, "latest")`.
- Test artifacts:
  - Detailed artifacts are written under `test_report/<timestamp>/...` (journal dumps, invocation dumps). These are invaluable for step-by-step diagnosis of version compatibility scenarios.
- Logs:
  - Log4j2 is set up; use logger `LogManager.getLogger()` in tests. Consider increasing verbosity with env if necessary.
- Parallelism and Isolation:
  - Some suites use `@Isolated` and `@Execution(SAME_THREAD)` due to shared runtime state. If your tests interact with the same cluster state or rely on fixed retry timing, mirror these annotations to avoid flakiness.

## Code Style and Quality

- Formatting: Spotless with ktfmt. Run `./gradlew spotlessApply` to fix formatting; CI will fail on `spotlessCheck` if not clean.
- License headers: automatically applied via Spotless; new Kotlin files should include the standard header (see `config/license-header`).
- Dependency licenses: `./gradlew generateLicenseReport` produces CSV reports; excludes/bundles configured for KotlinX and BOMs.

## Common Pitfalls

- Forgetting Java 21: the build will fail or tests will not run. Ensure your local JDK is 21+.
- Skipping codegen: building/running without triggering generators may cause missing symbols. Always run via Gradle tasks (`build`, `test`, or `run`) which are wired to invoke generators.
- Image pulling: default behavior pulls upstream images; if you expect local images, set `--image-pull-policy=CACHED` or tag images with `restate.local/*` or `localhost/*`.
- Version compatibility tests: ensure suspended/paused invocations are resumed after deploying new endpoints; see the `resumeInvocations` pattern in `FrontCompatibilityTest.NewVersion`.
