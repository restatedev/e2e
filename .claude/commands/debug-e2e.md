# Debug E2E Test Failures

Given a GitHub Actions run URL or PR URL, download test reports and investigate all test failures.

## Arguments
- $ARGUMENTS: A GitHub Actions run URL (e.g. https://github.com/restatedev/e2e/actions/runs/12345) or a PR URL (e.g. https://github.com/restatedev/e2e/pull/406)

## Instructions

### Step 1: Download test reports

Run the download script:
```
bash .tools/download-test-reports.sh "$ARGUMENTS"
```

Note the output directory path from the script output (it prints "Done! Reports saved to: ...").

### Step 2: Find all test failures

For each `TEST-junit-jupiter.xml` file in the downloaded report directory, parse the JUnit XML to find all `<testcase>` elements that contain a `<failure>` or `<error>` child element. Extract:
- The test class name (`classname` attribute)
- The test method name (`name` attribute)
- The failure/error message and type

Present a summary table of all failures found across all XML files, grouped by the report subdirectory (e.g. `default`, `threeNodes`, `alwaysSuspending`, etc.).

### Step 3: Investigate each failure

For each failed test, investigate the root cause using the log files. The JUnit XML contains minimal info -- the real debugging data is in the log directories.

The report directory structure is:
```
<report-dir>/<timestamp>/<config>/
  TEST-junit-jupiter.xml          # JUnit results (already parsed)
  testrunner.log                   # Top-level test runner log
  testrunner.stdout                # Test runner stdout
  <TestClassName>/                 # One subdirectory per test class
    testRunner.log                 # Per-test-class runner log
    runtime_0.log                  # Restate runtime log (suffix _0 = restart count 0)
    runtime_1.log                  # If runtime restarted, this is restart 1, etc.
    kafka_0.log                    # Kafka broker log (if applicable)
    state_dump.json                # Runtime state dump
    sys_invocation_dump.json       # Invocation table dump
    sys_journal_dump.json          # Journal table dump
    sys_journal_events_dump.json   # Journal events dump
    sys_service_dump.json          # Service registration dump
    sys_deployment_dump.json       # Deployment table dump
    deployer_environment.json      # Deployer environment config
```

For each failed test:
1. Go to the subdirectory matching the test's class name (the simple class name, not the fully qualified one)
2. Read the `testRunner.log` first -- it shows the test execution flow and often reveals the immediate cause. Note: this file may be absent; in that case there may be a `test-exceptions.log` with the test exception, and the execution flow is only in the top-level `testrunner.log`
3. If the failure involves a timeout or unexpected runtime behavior, check `runtime_0.log` (and higher restart counts if they exist) for errors, panics, or unexpected state
4. If the failure involves Kafka, check `kafka_0.log`
5. Check `sys_invocation_dump.json` to see the state of invocations -- look for stuck, failed, or unexpected invocation states
6. Check `sys_journal_dump.json` and `sys_journal_events_dump.json` if the failure seems related to journal/state issues

#### Deployment failures (TimeoutException in `beforeAll` / `RestateDeployer.deployRuntime`)

If the error stack trace goes through `RestateDeployerExtension.beforeAll` -> `RestateDeployer.deployAll` -> `deployRuntime`, the environment never came up -- this is an infra problem, not a test logic problem. Investigate differently:

- `deployRuntime` starts all runtime containers concurrently and waits at most **150 seconds** total (`CompletableFuture.allOf(...).get(150, SECONDS)`). Each individual container gets up to 3 testcontainers start attempts with a 120s `/restate/health` HTTP wait each. Multiple `runtime*_N.log` files with suffixes `_0, _2, _4` correspond to these retry attempts (~2 minutes apart).
- Build a **start timeline**: grep the first `^20..-` timestamp line of every `runtime*_N.log` file. A node whose first log line is minutes later than its siblings (or missing entirely) means its container never actually started -- focus on why docker couldn't start it, not on the Restate logs.
- **threeNodes configs**: only the node named `runtime` has `RESTATE_AUTO_PROVISION=true`; `runtime-1`/`runtime-2` point their metadata client at `http://runtime:5122`. If followers loop on `Metadata store has not been provisioned yet` or DNS `Temporary failure in name resolution` for host `runtime`, the leader container is not on the docker network (not started) -- those follower errors are a symptom, not the cause.
- For docker-level causes, search the **top-level** `testrunner.log` for: `Error during callback` (docker-java pull/exec failures), `502 Bad Gateway` / registry errors (GHCR outages -- the pull policy always re-pulls `restate:main`, so a registry blip can wedge a container start for minutes), `Could not start container`, `port is already allocated`. Count occurrences per config (`grep -c` across all `*/testrunner.log`) to judge whether it's a transient infra flake (re-run the job) vs something in the PR.

Caveats when reading the top-level `testrunner.log`:
- Test classes run **in parallel** and all share this log with no thread/class marker; container hostnames like `runtime` are reused by every class (each in its own docker network). Attribute lines by timestamp window (the `Writing container logs to .../<TestClassName>` line marks each class's deploy start) and by container ID, never by hostname alone.
- After the 150s deploy timeout fires, testcontainers retry threads keep restarting containers in the background. Log files and "started and is healthy" lines with timestamps *after* the test failure are zombie-retry noise, not evidence the deploy worked.

### Step 4: Report findings

For each failure, provide:
1. **Test**: fully qualified test name
2. **Config**: which configuration it failed in (default, threeNodes, etc.)
3. **Root cause**: what actually went wrong based on log analysis
4. **Key evidence**: the most relevant log snippets (keep them short but include enough context)
5. **Suggestion**: if you can identify a likely fix or area to investigate in the source code

If multiple failures share the same root cause, group them together.