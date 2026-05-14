# Restate E2E

This repository contains test suites for the Restate ecosystem, organized as a multi-module Gradle project.

## Modules

| Module | Description |
|--------|-------------|
| [`infra`](infra/) | Shared test infrastructure: TestContainers orchestration, JUnit integration, OpenAPI admin client, runtime config schema |
| [`e2e-tests`](e2e-tests/) | End-to-end integration tests for the Restate runtime |
| [`sdk-tests`](sdk-tests/) | Conformance tests for Restate SDK implementations |

See each module's README for usage details.

## Requirements

- JVM >= 21
- Docker

## Building

```shell
./gradlew build
```
