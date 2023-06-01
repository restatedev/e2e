# e2e
E2E tests for Restate

## Modules

* `services` contains a collection of services for e2e testing:
  * [`node-services`](services/node-services) contains the Node SDK services
  * [`java-services`](services/java-services) contains the Java SDK services
* `test-utils` contains utilities to develop e2e tests
* `tests` contains the test code
* `contracts` contains the different protobuf definitions, used by services and tests

## Setup local env

To run locally, you need to setup two env variables to download artifacts from Github Packages:

```bash
export GH_PACKAGE_READ_ACCESS_USER=<yourusername>
export GH_PACKAGE_READ_ACCESS_TOKEN=<PAT>
```

To create a PAT check https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token

You also need to login to the container registry using podman/docker (the command is the same for both):

```shell
docker login ghcr.io --username $GH_PACKAGE_READ_ACCESS_USER --password $GH_PACKAGE_READ_ACCESS_TOKEN
```

> *Note*
> Intellij might not pick up the env variables you set, so you might have to manually substitute the values of GH_PACKAGE_READ_ACCESS_USER and GH_PACKAGE_READ_ACCESS_TOKEN to let Intellij build the project. See https://intellij-support.jetbrains.com/hc/en-us/community/posts/360010612199-Unable-to-set-environmental-variables-for-Gradle-build

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
gradle :services:node-services:installLocalTypescriptSdk 
```

This will build the Typescript SDK, pack it with `npm pack`, and copy it over to the node-services directory and install it.

You can include `gradle :services:node-services:installLocalTypescriptSdk` in the build process by setting `TYPESCRIPT_SDK_LOCAL_BUILD=true`.

### How to test Restate runtime changes

You can manually build a docker image in the restate project using `just docker`. Then set the environment variable `RESTATE_RUNTIME_CONTAINER` with the tag of the newly created image (printed at the end of the docker build log).
