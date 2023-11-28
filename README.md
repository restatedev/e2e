# e2e
E2E tests for Restate

## Modules

* `services` contains a collection of services for e2e testing:
  * [`node-services`](services/node-services) contains the Node SDK services
  * [`java-services`](services/java-services) contains the Java SDK services
* `test-utils` contains utilities to develop e2e tests
* `tests` contains the test code
* `contracts` contains the different protobuf definitions, used by services and tests

## Run tests

To run tests, just execute:

```shell
gradle build
```

This will populate your local image registry with the various service containers, required for testing, and then execute the tests.

### Tests

Source code of test runners is located in the [Tests project](tests), in particular:

* [`dev.restate.e2e`](tests/src/test/kotlin/dev/restate/e2e) contains common tests to all SDKs, testing the various SDK features
* [`dev.restate.e2e.runtime`](tests/src/test/kotlin/dev/restate/e2e/runtime) contains tests for some specific runtime behavior
* [`dev.restate.e2e.java`](tests/src/test/kotlin/dev/restate/e2e/java) contains tests for Java SDK specific features
* [`dev.restate.e2e.node`](tests/src/test/kotlin/dev/restate/e2e/node) contains tests for Node SDK specific features

### Test configurations

Currently, we run tests in the following configurations:

* `gradle :tests:test`: Default runtime configuration
* `gradle :tests:testAlwaysSuspending`: Runtime setup to always suspend after replay, to mimic the behavior of RequestResponse stream type
* `gradle :tests:testSingleThreadSinglePartition`: Runtime setup with a single thread and single partition
* `gradle :tests:testPersistedTimers`: Runtime setup with timers in memory = 1, to trigger timer queue spilling to disk
* `gradle :tests:testLazyState`: Runtime setup disabling eager state when invoking the service endpoint

## Developing tests

### Parallel test execution

The test setup uses Gradle's `maxParallelForks` to distribute the test classes among different JVMs, providing parallelism per class.

Within the test classes, it is possible to tag individual test methods to be executed in parallel.
Usually a test method is considered safe to execute in parallel when:

* Doesn't invoke directly or transitively singleton services
* When invoking directly or transitively keyed services, it uses a key with a random component, e.g. like a UUID
* Doesn't use additional stateful containers
* The containing test class uses `RestateDeployerExtension`

To tag a test method to be executed in parallel, use the annotation `@Execution(ExecutionMode.CONCURRENT)`.

## Debugging

### `VerificationTest` seed

`VerificationTest` is using a random seed to generate the execution tree, logged at the beginning of the test. 
You can fix the seed to use setting the environment variable `E2E_VERIFICATION_SEED`.

### Test report and container logs

For each deployment of `RestateDeployer`, the `stdout` and `stderr` of the containers and the `docker inspect` info are written in `tests/build/test-results/[test-configuration]/container-logs/[test-name]`.

### How to test Java SDK changes

In order to test local changes to the `sdk-java`, you need to check it out under `../sdk-java`.
When building the `e2e` project you have to set the environment variable `JAVA_SDK_LOCAL_BUILD=true` 
to include `sdk-java` as a composite build and substitute the `dev.restate.sdk:sdk-java` dependency with it.
The build will fail if Gradle cannot find the `sdk-java` project.

### How to test Typescript SDK changes

In order to test local changes to the `sdk-typescript`, you need to check it out under `../sdk-typescript`.
Then run:

```shell
gradle :services:node-services:installLocalSdkTypescript 
```

This will build the Typescript SDK, pack it with `npm pack`, and copy it over to the node-services directory and install it.

You can include `gradle :services:node-services:installLocalSdkTypescript` in the build process by setting `SDK_TYPESCRIPT_LOCAL_BUILD=true`.

### How to test Restate runtime changes

You can manually build a docker image in the restate project using `just docker`. Then set the environment variable `RESTATE_RUNTIME_CONTAINER` with the tag of the newly created image (printed at the end of the docker build log).

### Retain runtime state after test

To retain the runtime RocksDB and Meta state, set the environment variable `E2E_MOUNT_STATE_DIRECTORY=true` to mount the state directory in the same directory of the [container logs](#test-report-and-container-logs).

## Running the services

For running the services see [services/README.md](services/README.md).
