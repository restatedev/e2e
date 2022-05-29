# e2e
E2E tests for Restate

## Modules

* `functions` contains a collection of functions for e2e testing
* `test-utils` contains utilities to develop e2e tests
* `tests` contains the test code

## Setup local env

To run locally, you need to setup two env variables to download artifacts from Github Packages:

```bash
export GH_PACKAGE_READ_ACCESS_USER=<yourusername>
export GH_PACKAGE_READ_ACCESS_TOKEN=<PAT>
```

To create a PAT check https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token

You also need to login to the container registry using podman/docker (the command is the same for both):

```shell
podman login ghcr.io --username $GH_PACKAGE_READ_ACCESS_USER --password $GH_PACKAGE_READ_ACCESS_TOKEN
```

### How to test with latest sdk changes

In order to test local changes to the `java-sdk`, you need to check it out under `../java-sdk`.
When building the `e2e` project you have to set the environment variable `E2E_LOCAL_BUILD=true` to include `java-sdk` as a composite build and substitute the `dev.restate.sdk:java-sdk` dependency with it.
The build will fail if Gradle cannot find the `java-sdk` project.

## Run tests

To run tests, first build the project with:

```shell
gradle build jibDockerBuild -x test
```

This will populate your local image registry with the various function containers, required for testing.

Now just execute:

```shell
gradle test
```

To run the tests.

### Picking the container image for testing

In order to always pull the runtime image you can set the environment variable `E2E_IMAGE_PULL_POLICY=always`.
If you want to test against a runtime image that is available locally, unset this environment variable to let testcontainers pick the local image. 