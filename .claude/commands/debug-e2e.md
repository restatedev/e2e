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
2. Read the `testRunner.log` first -- it shows the test execution flow and often reveals the immediate cause
3. If the failure involves a timeout or unexpected runtime behavior, check `runtime_0.log` (and higher restart counts if they exist) for errors, panics, or unexpected state
4. If the failure involves Kafka, check `kafka_0.log`
5. Check `sys_invocation_dump.json` to see the state of invocations -- look for stuck, failed, or unexpected invocation states
6. Check `sys_journal_dump.json` and `sys_journal_events_dump.json` if the failure seems related to journal/state issues

### Step 4: Report findings

For each failure, provide:
1. **Test**: fully qualified test name
2. **Config**: which configuration it failed in (default, threeNodes, etc.)
3. **Root cause**: what actually went wrong based on log analysis
4. **Key evidence**: the most relevant log snippets (keep them short but include enough context)
5. **Suggestion**: if you can identify a likely fix or area to investigate in the source code

If multiple failures share the same root cause, group them together.