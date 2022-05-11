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