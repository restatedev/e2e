# Verification tests

The verification test is a randomly generated integration test that involves generating large
command trees which are executed across many service keys, synchronously and asynchonously,
incrementing state values, and then finally the values are checked against a simulation.

The service code for this test lives in [services/node-services/src/interpreter](../../services/node-services/src/interpreter).

## Running the test locally

To run the test locally you can run [the run script](../../services/node-services/compose/run.sh).

## Updating the Typescript SDK

Whenever you update the Typescript SDK, you need to make sure that the [e2e-node-services](https://github.com/orgs/restatedev/packages/container/package/e2e-node-services) Docker image is updated.
The image gets automatically rebuilt with every push to `main`.
You can also manually rebuild the image via:

```shell
$ docker build --platform linux/arm64,linux/amd64 -t ghcr.io/restatedev/e2e-node-services --push services/node-services
```