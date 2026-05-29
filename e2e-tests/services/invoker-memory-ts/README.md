# invoker-memory-ts

TypeScript counterpart of the in-process Kotlin services used by
`InvokerMemoryTest` (`MemoryPressureService` and `StatefulObject`).

Used by `InvokerMemoryTest` when `INVOKER_MEMORY_TEST_SDK=ts` is set; the
default path remains the in-process Kotlin services.

## Publishing the multi-arch image to ghcr.io

The CI test job does not build this image — it pulls a pre-built multi-arch
image from `ghcr.io`. Republish manually whenever the source changes:

```bash
# Requires: docker buildx, ghcr.io login (`docker login ghcr.io`).
# The buildx builder must support both linux/amd64 and linux/arm64.

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ghcr.io/restatedev/e2e-invoker-memory-ts:0.1.0 \
  --push \
  e2e-tests/services/invoker-memory-ts
```

Verify the resulting manifest contains both architectures:

```bash
docker manifest inspect ghcr.io/restatedev/e2e-invoker-memory-ts:0.1.0
```

Set the GHCR package visibility to **public** so workflow runners can pull
without authentication.

## Local development

```bash
cd e2e-tests/services/invoker-memory-ts
npm install
npm run build
PORT=9080 node dist/index.js
```

Then point a local Restate runtime at it (or use the test in debug mode).
