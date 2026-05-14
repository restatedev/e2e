# Restate E2E Tests

End-to-end tests for the Restate runtime. Tests are deployed as Docker containers and orchestrated via TestContainers.

## Requirements

- JVM >= 21
- Docker

## Running locally

Run all suites:

```shell
./gradlew :e2e-tests:run
```

Run a specific suite:

```shell
./gradlew :e2e-tests:run --args='run --test-suite=default'
```

Run a single test class within a suite:

```shell
./gradlew :e2e-tests:run --args='run --test-suite=default --test-name=IngressTest'
```

## Available test suites

| Suite | Description |
|-------|-------------|
| `default` | Core tests, single Restate node |
| `threeNodes` | Multi-node cluster (3 nodes, 4 partitions) |
| `alwaysSuspending` | Tests with zero inactivity timeout (every invocation suspends) |
| `threeNodesAlwaysSuspending` | Multi-node + always-suspending |
| `versionCompat` | Version compatibility tests |

Pass `all` (the default) to run all suites sequentially.

## Options

| Option | Env var | Description |
|--------|---------|-------------|
| `--restate-container-image` | `RESTATE_CONTAINER_IMAGE` | Restate runtime image to use (default: `ghcr.io/restatedev/restate:main`) |
| `--image-pull-policy` | | `ALWAYS` (default) or `CACHED`. `ALWAYS` skips pull for images prefixed with `restate.local` or `localhost` |
| `--report-dir` | `TEST_REPORT_DIR` | Output directory for test reports (default: `test_report/<timestamp>`) |
| `--exclusions-file` | `TEST_EXCLUSIONS_FILE` | YAML file listing tests to skip |
| `--sequential` | | Disable parallel test execution (useful with Podman) |

All environment variables prefixed with `RESTATE_` or `RUST_` are forwarded to every Restate runtime container.

## Exclusions file

To skip known flaky or broken tests, provide a YAML file:

```yaml
exclusions:
  default:
    - dev.restate.sdktesting.tests.SomeTest.someMethod
  threeNodes:
    - dev.restate.sdktesting.tests.AnotherTest.anotherMethod
```

Pass it with `--exclusions-file=exclusions.yaml`. At the end of a run, the tool writes `exclusions.new.yaml` to the report directory containing the current run's failures merged with the existing exclusions — ready to use as the exclusions file for the next run.

## Using as a GitHub Action

Place this in any workflow (from a different repo or this one):

```yaml
- uses: restatedev/e2e/e2e-tests@main
  with:
    testArtifactOutput: e2e-test-report
    restateContainerImage: ghcr.io/restatedev/restate:main
    # Optional: paste YAML exclusion content directly
    # exclusions: |
    #   exclusions:
    #     default:
    #       - SomeTest.someMethod
```
