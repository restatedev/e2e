# Restate end-to-end tests

This repository contains Restate e2e tests.

## Running locally

You need JVM >= 21.

To run all the tests:

```shell
./gradlew run
```

To pass args to the tool, e.g. to run an individual test:

```shell
./gradlew run --args 'run --test-suite=<SUITE> --test-name=<TEST_NAME>'
```

To change the runtime container image, use `--restate-container-image=<RUNTIME_CONTAINER_IMAGE>`.

By default, the tool will always pull images, unless they have the repository prefix `restate.local` or `localhost`. To always try to use the local cache, use `--image-pull-policy=CACHED`.

All the environment variables prefixed with `RUST_` and `RESTATE_` will be propagated to every deployed runtime container. 
