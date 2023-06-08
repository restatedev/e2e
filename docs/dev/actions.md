# Actions

The e2e repo is capable of running integration tests that use a variety of components,
including the runtime, the typescript sdk, and the java sdk. The GitHub action in 
[e2e.yaml](../../.github/workflows/e2e.yaml) is intended to be called from other repos 
to allow integration tests to be run on unmerged versions of components in those repos,
for example as part of a PR test job. These calls happen via GitHub's 
[Reusable Workflow](https://docs.github.com/en/actions/using-workflows/reusing-workflows)
feature, which essentially means that the e2e workflow will run *inside* the calling repo,
as if it was inlined there.

The design decision was made that the e2e workflow should not know how to build every
component that might want to call it. Instead, it should know how to receive artifacts
from callers. The simplest design for this is using GitHub's 
[upload-artifact](https://github.com/actions/upload-artifact) and
[download-artifact](https://github.com/actions/download-artifact) builtin actions. These
actions only work within a given repo, but allow artifacts to be shared during the course
of a workflow, instead of them only being available at the end of a workflow. Given that
workflow calls are essentially inlined, the same-repo restriction is not a problem.

To reduce size and speed up builds, the restate artifact that is sent for the e2e job is
a debug binary, not a full Docker image with a release binary. This may change in future.
The e2e workflow uses a two-line Dockerfile in order to place the received binary into
`ghcr.io/restatedev/restate:latest`.

The artifact sent for the typescript sdk is a tar.gz package created via `npm pack`. This
is simply installed directly into the node-services directory.

A complication comes from the goal to allow the e2e workflow to be called by a human in
the actions UI for this repo, using commits from other repos that refer to previously built
artifacts. In this case, the download-artifact action will not work, because
they will look for artifacts in the e2e repo. Instead we use a more flexible action,
[dawidd6/action-download-artifact](https://github.com/dawidd6/action-download-artifact),
which is able to download artifacts from other repos based on a provided commit. This relies
on access token secrets in this repo which have the actions read permission on the 
required repos with artifacts;`SDK_TYPESCRIPT_ACTION_READ_TOKEN` and
`RESTATE_ACTION_READ_TOKEN`.
