# Restate SDK Test Suite

Conformance tests for Restate SDK implementations. Given a Docker image that implements the [service contracts](src/main/kotlin/dev/restate/sdktesting/contracts/), the suite verifies correct SDK behavior across all test suites.

## Requirements

- JVM >= 21
- Docker

## Running locally

### From Gradle (build from source)

Run all suites against a service image:

```shell
./gradlew :sdk-tests:run --args='run <service-image>'
```

Run a specific suite:

```shell
./gradlew :sdk-tests:run --args='run --test-suite=default <service-image>'
```

Run a single test:

```shell
./gradlew :sdk-tests:run --args='run --test-suite=default --test-name=State <service-image>'
```

### From the pre-built JAR

Download the latest JAR from [GitHub Releases](https://github.com/restatedev/e2e/releases) and run:

```shell
java -jar sdk-tests.jar run <service-image>
java -jar sdk-tests.jar run --test-suite=default <service-image>
```

### Debug mode (service running locally, not in a container)

Useful during SDK development to run a single test against a locally running service:

```shell
java -jar sdk-tests.jar debug --test-name=State 9080
# or with a named container:
java -jar sdk-tests.jar debug --test-name=State myService=9080
```

Options available in debug mode:

| Option | Description |
|--------|-------------|
| `--test-suite` | Suite to use for environment setup (default: `default`) |
| `--test-name` | Test class to run (required) |
| `--retain-after-end` | Keep Docker network alive after the test (for inspection) |
| `--mount-state-directory` | Mount a local directory as Restate data directory |
| `--local-ingress-port` | Bind Restate ingress to a specific host port |
| `--local-admin-port` | Bind Restate admin to a specific host port |

## Available test suites

| Suite | Description |
|-------|-------------|
| `default` | Core tests, single Restate node |
| `threeNodes` | Multi-node cluster (3 nodes, 4 partitions) |
| `alwaysSuspending` | Tests with zero inactivity timeout (every invocation suspends) |
| `threeNodesAlwaysSuspending` | Multi-node + always-suspending |
| `singleThreadSinglePartition` | Single thread, single partition |
| `lazyState` | Tests with eager state loading disabled |
| `lazyStateAlwaysSuspending` | Lazy state + always-suspending |
| `persistedTimers` | Tests with in-memory timer limit set to 1 (forces persistence) |

Pass `all` (the default) to run all suites sequentially.

## Options

| Option | Env var | Description |
|--------|---------|-------------|
| `--restate-container-image` | `RESTATE_CONTAINER_IMAGE` | Restate runtime image (default: `ghcr.io/restatedev/restate:main`) |
| `--image-pull-policy` | | `ALWAYS` (default) or `CACHED` |
| `--report-dir` | `TEST_REPORT_DIR` | Output directory for test reports (default: `test_report/<timestamp>`) |
| `--exclusions-file` | | YAML file listing tests to skip |
| `--service-container-env-file` | | `.env` file whose variables are injected into the service container |
| `--custom-tests-file` | | YAML file defining custom test commands (for `customTests` suite) |
| `--sequential` | | Disable parallel test execution |

## Exclusions file

```yaml
exclusions:
  default:
    - dev.restate.sdktesting.tests.State.add
  alwaysSuspending:
    - dev.restate.sdktesting.tests.Sleep.sleepAndExpire
```

At the end of a run, the tool writes `exclusions.new.yaml` to the report directory.

## Custom tests file

The `customTests` suite runs arbitrary shell commands as tests. Provide a YAML file:

```yaml
tests:
  - name: my-custom-test
    command: "curl -s http://$RESTATE_INGRESS_URL/MyService/myHandler | jq ."
```

The environment variables `RESTATE_INGRESS_URL` and `RESTATE_ADMIN_URL` are available to each command.

## Using as a GitHub Action

```yaml
- uses: restatedev/e2e/sdk-tests@v1.2.3
  with:
    testArtifactOutput: sdk-test-report
    serviceContainerImage: ghcr.io/myorg/my-sdk-service:latest
    restateContainerImage: ghcr.io/restatedev/restate:main
    # exclusionsFile: exclusions.yaml
    # serviceContainerEnvFile: service.env
```

The action downloads the JAR matching the tag it was called with (i.e. `v1.2.3`) from GitHub Releases.

## Releases

A new release is created by pushing a git tag. The CI workflow builds the shadow JAR and attaches it to the GitHub Release as `sdk-tests.jar`.
