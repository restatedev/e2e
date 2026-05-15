---
name: add-sdk-conformance-test
description: Add a new test to the SDK conformance test suite. Use when the user wants to add a new sdk test, conformance test, or test a new SDK feature across all SDK implementations.
user-invocable: true
---

# Adding a New SDK Conformance Test

The `sdk-tests` module is a **conformance tool** — it defines contracts that SDK implementations must satisfy and test runners that verify them. It contains NO implementation code.

## Architecture

- **Contracts** (`sdk-tests/src/main/kotlin/dev/restate/sdktesting/contracts/`) — Kotlin interfaces (`@Service`, `@VirtualObject`, `@Workflow`) that each SDK implements. These define the wire API (service name, handler names, JSON field names).
- **Tests** (`sdk-tests/src/main/kotlin/dev/restate/sdktesting/tests/`) — JUnit 5 test classes that drive contracts through the Restate ingress client.

**Never add implementation code to sdk-tests.** Only interfaces in contracts, only test logic in tests.

## Step 1: Expand the contracts (if needed)

Edit the relevant contract interface only if strictly needed. The main ones:

- `VirtualObjectCommandInterpreter` — interpreter for combinator/signal/awakeable tests; the workhorse for most feature tests
- `TestUtilsService` — utility handlers (cancel, signal resolve/reject, etc.)

**Contract rules:**
- Data classes → `@Serializable`; sealed hierarchies → `@SerialName("camelCase")` discriminator
- Handler inputs must be a single type — wrap multiple fields in a `@Serializable` data class
- `@Handler` for exclusive handlers, `@Shared` for shared handlers

### VirtualObjectCommandInterpreter — key types

**AwaitableCommand** (sub-operations that can be composed):
- `CreateAwakeable(awakeableKey)`, `CreateSignal(signalName)`, `Sleep(timeoutMillis)`, `RunReturns(value)`, `RunThrowTerminalException(reason)`

**Command** (top-level interpreter steps):
- `AwaitOne(command)` — await a single sub-operation
- `AwaitAny(commands)` — first to complete (race); throws if winner failed
- `AwaitAnySuccessful(commands)` — first successful or all failed (legacy)
- `AwaitFirstCompleted(commands)` — first to complete (race)
- `AwaitFirstSucceededOrAllFailed(commands)` — first success, or throws if all fail
- `AwaitAllSucceededOrFirstFailed(commands)` — all succeed → pipe-joined `"v0|v1"`; throws on first fail
- `AwaitAllCompleted(commands)` — all settle → pipe-joined `"ok:v0|err:reason|ok:v2"`

**TestUtilsService:**
- `resolveSignal(ResolveSignalRequest(invocationId, signalName, value))`
- `rejectSignal(RejectSignalRequest(invocationId, signalName, reason))`

## Step 2: Write the test

### Test class boilerplate

```kotlin
class MyFeature {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(
          ServiceSpec.defaultBuilder()
              .withServices(VirtualObjectCommandInterpreter::class, TestUtilsService::class))
    }
  }

  @Test
  @DisplayName("Human-readable description")
  @Execution(ExecutionMode.CONCURRENT)
  fun myTest(@InjectClient ingressClient: Client) = runTest {
    // ...
  }
}
```

### Client patterns

```kotlin
// Build clients
val voClient = ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(UUID.randomUUID().toString())
val svcClient = ingressClient.toService<TestUtilsService>()

// Call and get result immediately
val result = voClient.request { myHandler(req) }.options(idempotentCallOptions).call().response

// Send (non-blocking) + attach later — REQUIRED for signals
val sendResponse = voClient.request { myHandler(req) }.options(idempotentCallOptions).send()
val invocationId = sendResponse.invocationId()
// ... resolve/reject signals ...
val result = sendResponse.attachSuspend().response

// Expect a terminal error
assertThat(runCatching { sendResponse.attachSuspend().response }.exceptionOrNull())
    .message().contains("expected substring")

// Poll until condition (awakeables only — not needed for signals)
await withAlias "description" untilAsserted {
  assertThat(voClient.request { hasAwakeable("key") }.call().response).isTrue()
}
```

### Awakeable vs Signal patterns

**Awakeables** — identified by a unique runtime ID stored in VirtualObject state:
- Send the `interpretCommands` call and poll `hasAwakeable(key)` before resolving
- Resolve/reject via `interpreterClient.request { resolveAwakeable(ResolveAwakeable(key, value)) }`

**Signals** — identified by invocation ID + name; no pre-registration needed:
- `.send()` → get `invocationId()` → resolve/reject via `TestUtilsService.resolveSignal/rejectSignal` → `attachSuspend()`
- No polling required — signals can be sent before or after the handler starts waiting

## Step 3: Verify it compiles

```bash
./gradlew :sdk-tests:compileKotlin
```

## Step 4: Run against a local SDK image

Build the SDK Docker image (example for TypeScript SDK):

```bash
# From the sdk-typescript repo root
podman build -t e2e-ts:local -f packages/tests/restate-e2e-services/Dockerfile .
```

Run just the new test class:

```bash
./gradlew :sdk-tests:run --args='run --sequential --image-pull-policy=CACHED --test-suite=default --test-name=MyFeature --service-container-image=localhost/e2e-ts:local'
```

## Step 5: Update SDK implementations

After adding a new contract or command type, you must update each SDK's test service implementation. The TypeScript SDK (the reference implementation) lives in `sdk-typescript`:
- Main: `packages/tests/restate-e2e-services/src/virtual_object_command_interpreter.ts` and `test_utils.ts`
- Gen: `packages/libs/restate-sdk-gen/test-services/src/vo-command-interpreter.ts` and `test-utils.ts`

Use the `update-sdk-test-contracts` skill in the sdk-typescript repo for guidance on the implementation patterns.
