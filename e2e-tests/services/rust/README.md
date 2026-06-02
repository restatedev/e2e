# e2e-test-services (Rust)

Aggregate Rust binary hosting every Rust-side service implementation consumed
by the JVM e2e test suite. Packaged and published as a single multi-arch
Docker image at **`ghcr.io/restatedev/e2e-test-services-rs`**.

The binary reads the `SERVICES` environment variable at startup
(comma-separated list of service names) and binds only those services to its
Restate HTTP/2 endpoint. The Testcontainers-based deployer in
`infra/.../ServiceSpec.kt` already injects this variable for any sibling
service container — see `ServiceSpec.withServices(...)` callers. If `SERVICES`
is missing or empty all known services are bound (a convenient local-dev
default).

## Currently hosted services

| Restate name             | Module             | Kotlin contract (under `e2e-tests/.../contracts/`) |
| ------------------------ | ------------------ | -------------------------------------------------- |
| `MemoryPressureService`  | `invoker_memory`   | `MemoryPressureService.kt`                         |
| `StatefulObject`         | `invoker_memory`   | `StatefulObject.kt`                                |

If you rename a Restate-visible name on either side you must update the other
in the same change — a mismatch surfaces immediately as a 404 from the Restate
ingress on the first test call.

## Adding a new service

1. Create `src/<my_service>.rs` with the trait + impl, decorated with
   `#[restate_sdk::service]` / `#[restate_sdk::object]` and `#[name = "..."]`
   to fix the Restate-visible camelCase name.
2. Declare the module in `src/main.rs`: `mod <my_service>;` and import the
   impl (and trait, so `.serve()` is in scope).
3. Add an entry to `ALL_SERVICES` and a `match` arm in `bind_services(...)`.
4. Add a matching Kotlin `@Service` / `@VirtualObject` interface under
   `e2e-tests/src/main/kotlin/dev/restate/sdktesting/contracts/`.
5. Update the "Currently hosted services" table above.
6. Rebuild and republish the image (bump the tag, see below) and update the
   `DEFAULT_E2E_RS_IMAGE` constant in any test that pins the tag.

## Prerequisites

- Rust toolchain ≥ 1.90 (`rust-toolchain.toml` pins this).
- Docker with `buildx` enabled.
- A multi-arch builder, created once per machine:

  ```bash
  docker buildx create --name multiarch --use
  ```

## Local development

```bash
cd e2e-tests/services/rust
cargo build --release

# Bind every service (default):
PORT=9080 target/release/e2e-test-services

# Bind a strict subset:
PORT=9080 SERVICES=MemoryPressureService target/release/e2e-test-services
```

Point a local Restate runtime at the listening port, or run the e2e test in
debug mode with the local-forward service deployment config.

## Building and publishing the multi-arch image to ghcr.io

CI does **not** build this image — it pulls a pre-built multi-arch image
from `ghcr.io`. Republish manually whenever any service implementation, the
`Cargo.toml`, or the `Dockerfile` changes meaningfully.

```bash
# 1. Authenticate to ghcr.io (once per machine):
echo $GITHUB_TOKEN | docker login ghcr.io -u <gh-username> --password-stdin

# 2. Build for linux/amd64 + linux/arm64 and push:
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ghcr.io/restatedev/e2e-test-services-rs:0.1.0 \
  --push \
  e2e-tests/services/rust

# 3. Verify the manifest lists both arches:
docker manifest inspect ghcr.io/restatedev/e2e-test-services-rs:0.1.0
```

**Bump cadence**: bump the tag (`0.1.0` → `0.1.1` for patches, `0.2.0` when a
new service is added or an existing handler signature changes) and update
the `DEFAULT_E2E_RS_IMAGE` constant in any test that pins the tag.

**GHCR package visibility**: set to **public** so workflow runners (and other
contributors) can pull without authentication. UI:
<https://github.com/orgs/restatedev/packages/container/e2e-test-services-rs/settings>.

## Discovery manifest sanity check

To confirm the running binary exposes the handler names the Kotlin client
expects:

```bash
PORT=9080 SERVICES=MemoryPressureService,StatefulObject \
  target/release/e2e-test-services &

curl --http2-prior-knowledge \
  -H 'accept: application/vnd.restate.endpointmanifest.v3+json' \
  http://127.0.0.1:9080/discover \
  | jq '.services[] | {name, ty, handlers: [.handlers[].name]}'
```

Expected:

```json
{ "name": "MemoryPressureService", "ty": "SERVICE",
  "handlers": ["generate", "generateOversized"] }
{ "name": "StatefulObject", "ty": "VIRTUAL_OBJECT",
  "handlers": ["initState", "readState", "readLargeState"] }
```
