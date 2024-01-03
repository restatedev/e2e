# Verification tests

The verification test is a randomly generated integration test that involves generating large
command trees which are executed across many service keys, synchronously and asynchonously,
incrementing state values, and then finally the values are checked against a simulation.

The service code for this test lives in [services/node-services](../../services/node-services) as
the CommandInterpreter and CommandVerifier services. The e2es perform a normal verification test
run, and several recovery runs where either the service process or restate process are killed 
during the run.

## Test structure
The test involves a tree built from 6 command types
- Increment: The simplest command; gets the state field 'counter' and sets it again + 1
- SyncCall: Call another service key and await the result. Has subcommands which will be passed to the next key 
- AsyncCall: Call another service key and don't yet await the result. Also has subcommands
- AsyncCallAwait: Await a previous AsyncCall
- BackgroundCall: Use Restates oneWayCall mechanism to make a call where we don't expect a response. Has subcommands for the next key
- Sleep: Sleep for a specified number of millis

## Test parameters
The test takes 4 parameters, all of which form part of the service key for all subrequests,
so tests with different parameters cannot conflict in state. An idempotency state field is also set once
the parameters are seen for the first time, so additional calls should not have side effects.
- seed: An arbitrary string used to seed the rng for the command tree generation
- width: A command can have up to this many subcommands (randomly chosen) 
- depth: A command tree can go this deep at most; at this depth only leaf increment commands are created
- max_sleep_millis: Optional, defaults to 32768. All the sleep durations in the test will sum to this value

Larger width and depth create much larger trees which take longer to complete.
In the e2es we use a width of 3 and a depth of 14. Currently the tree size is quite variable
as these parameters only set a range within random numbers are chosen; a more predictable strategy
may be desirable in future.

## Running a test locally
The simplest way to run a verification test locally is to run a local restate:
```bash
docker run  -it --network=host ghcr.io/restatedev/restate:main
# or using a local version:
cargo run
```
Then start a `node-services` container:
```bash
SERVICES=verifier.CommandVerifier,interpreter.CommandInterpreter PORT=9080 docker run -p 9080:9080 -e SERVICES ghcr.io/restatedev/e2e-node-services:latest
# or using a local version:
cd services/node-services
npm link ../../../sdk-typescript # optionally, link to a local typescript sdk where `npm run build` has been run
npm run build
SERVICES=verifier.CommandVerifier,interpreter.CommandInterpreter npm run app
```

Then discover the services:
```bash
$ curl 127.0.0.1:9070/endpoints -H 'content-type: application/json' -d '{"uri": "http://localhost:9080"}'
{"services":["verifier.CommandVerifier","interpreter.CommandInterpreter"]}
```

Then execute a test with a given seed and set of parameters:
```bash
# these parameters match the tests in VerificationTest.kt. <width>-<depth>-<sleep>-<seed>
$ curl -s 127.0.0.1:8080/verifier.CommandVerifier/Execute --json '{"params": "3-14-5000-my-seed"}'
{}
# When this returns, the sync portion of the test is done, but async tasks are likely still executing
# Wait for output to stop in the Restate service
$ curl -s 127.0.0.1:8080/verifier.CommandVerifier/Verify --json '{"params": "3-14-5000-my-seed"}'
{"counters":{"84":2,"27":1,"30":1,"91":1,"23":1}}
# A failed output looks like this:
{"code":"internal","message":"[verifier.CommandVerifier-EQoIbXktc2VlZDEQAxgOIIgn-0188f6f65119797daf85039e6d67c941] [Verify]  Uncaught exception for invocation id: Incorrect value for target 14: expected 1, got 0"}
```

## Debugging a failed test
To understand a failed test, you first need its full set of parameters. The seed should be present in the logs of whatever failed.
For the other parameters, look at what created the job. In the e2e tests, the parameters are as above, width 3 depth 14 max_sleep_millis 5000.
In the long running tests, we currently use width 4, depth 14, and default max_sleep_millis.

The next thing you can try is running the same parameters a few times locally. This helps determine if this is a easily reproducible
failure, something that reproduces rarely, or perhaps something related to the test infra that led to a timeout that wasn't due to the test.

One thing that might help is saving the command tree locally:
```bash
curl -s 127.0.0.1:8080/verifier.CommandVerifier/Inspect --json '{"params": "3-14-5000-my-seed"}' | jq '.call'  > call.json
# You can execute a saved command tree directly, but this is *not* idempotent, so make sure to wipe Restate as needed
curl -s 127.0.0.1:8080/interpreter.CommandInterpreter/Call --json @call.json
```

Sometimes, it is possible to shave away sections of a command tree while still reproducing the issue, to arrive at
a minimal command tree that reproduces. This should make it very clear what sort of behaviour is causing a bug.

In cases where a tree rarely reproduces an issue, for example because it only happens if Restate restarts at the right time
or on a certain race condition, a better strategy is to evaluate Restate's internal state post-failure. Almost always, a verification
test fails because certain invocations are 'stuck' and are not processed to completion, blocking various increment commands
from eventually being processed leading to the correct final counters. Using the CLI, we can find such stuck invocations and
try and understand what is blocking them, or use logs to dig into their history.

```bash
$ cargo run --bin restate-cli
> select * from status; # this should only return service keys that are pending invocations; after a test, any row here is 'stuck'
> select * from journal; # this will return the journal entries of pending/stuck invocations
```
