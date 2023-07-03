# node-services

## Setting up npm to download the Restate Typescript npm package

The Typescript SDK package is currently a private package in Restate's GitHub repository.
You will only be able to run this example if you have access to the Typescript SDK repository.

To process, you need to make a GitHub token (classic) that can read packages.
Go to GitHub -> click on your profile -> settings -> developer settings (last option in the bar on the left)
-> generate new token -> select read and write packages -> generate the token -> don't close it!

Then do

    npm login --scope @restatedev --registry=https://npm.pkg.github.com

Fill in your git username and the GitHub token.

Now you should have something like this in ~/.npmrc:

    //npm.pkg.github.com/:_authToken=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxx
    @restatedev:registry=https://npm.pkg.github.com

## Install and build

To get all the dependencies required to develop the node services:

```shell
$ npm install
```

To build:

```shell
$ npm run build
```

To build the docker image:

```shell
$ gradle :services:node-services:dockerBuild
```

## Run proto code generation

To re-gen the `generated` directory:

```shell
$ npm run proto
```

## Lint and format

Linting is run together with `gradle check`, you can format using:

```shell
$ npm run format
```

## Running the services

The Node services can be run via:

```shell
SERVICES=<COMMA_SEPARATED_LIST_OF_SERVICES> gradle npm_run_app 
```

For the list of supported services see [here](src/app.ts).
