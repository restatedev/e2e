# e2e
E2E tests for Restate

## Modules

* `functions` contains a collection of functions for e2e testing
* `test-utils` contains utilities to develop e2e tests
* `tests` contains the test code

## Setup local env

To run locally, you need to setup two env variables to download artifacts from Github Packages:

```bash
export GITHUB_ACTOR=<yourusername>
export GITHUB_TOKEN=<PAM>
```

To create a PAM check https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token