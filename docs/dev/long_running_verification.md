# Long running tests

See [verification.md](verification.md) first for a primer on the verification tests.

## Overview

There is an EKS cluster running with verification tests running minutely across two Restate clusters; a normal cluster 
and a chaos cluster. In the chaos cluster, various chaos jobs (eg, kill the Restate pod, kill all service pods) run on a rotation
over the course of each hour. In both clusters, verification tests are run at the same seeds every minute - the seed is
the name of the Job eg `test-28112572` which includes a unix minute counter.

You can get credentials for this cluster using the `aws` cli:
```bash
# do this once; use https://d-99671f0c4b.awsapps.com/start and eu-central-1
aws configure sso
# do this every time your tokens expire
aws sso login
# do this once
aws eks update-kubeconfig --region eu-central-1 --name dev
```
And you can use the `kubectl` cli to inspect things:
```bash
$ kubectl -n restate get pods
NAME                           READY   STATUS      RESTARTS   AGE
restate-0                      2/2     Running     0          6d23h
test-28112572-8jt9b            0/1     Error       0          11d
...
test-28129574-92xnq            0/1     Completed   0          60s
verification-978697dc5-jvt8m   1/1     Running     0          10d
verification-978697dc5-w4qbb   1/1     Running     0          10d
verification-978697dc5-wxn9n   1/1     Running     0          10d
```

The same setup is present in the restate-chaos namespace, use `-n restate-chaos` instead there.

So we can see there is a single Restate node, and three service endpoints.
To create the `test` pods, we use a single CronJob called `test` which creates a test Job every minute.
The seed for the test run is the name of the Job.
```bash
$ kubectl -n restate get jobs
NAME            COMPLETIONS   DURATION   AGE
discover        1/1           5s         23d
test-28112572   0/1           11d        11d
test-28113175   0/1           11d        11d
test-28113361   0/1           11d        11d
test-28113670   0/1           11d        11d
test-28113753   0/1           10d        10d
test-28113838   0/1           10d        10d
test-28113972   0/1           10d        10d
test-28114021   0/1           10d        10d
test-28114060   0/1           10d        10d
test-28114121   0/1           10d        10d
test-28129563   1/1           53s        4m34s
test-28129564   1/1           96s        3m34s
test-28129565   1/1           37s        2m34s
test-28129566   0/1           94s        94s
test-28129567   0/1           34s        34s
```

Jobs create Pods repeatedly on a backoff until they exhaust their retry strategy and consider themself failed.
Up to 10 failed jobs are kept around, persisting the underlying pods which ran the test and allowing us to read their logs.
The most recent 3 successful jobs are also kept around. Jobs are expected to take a little while before succeeding; the Verify
step is run repeatedly until it succeeds, so we expect that there are a couple of very recent jobs that have neither succeeded or failed yet.

We can take a look at the Pods underlying a failed Job like so:
```bash
$ kubectl -n restate get pods --sort-by=metadata.creationTimestamp | grep test-28114121
test-28114121-6pkfw            0/1     Error               0          10d
test-28114121-srsbp            0/1     Error               0          10d
test-28114121-phhw5            0/1     Error               0          10d
test-28114121-hsgqw            0/1     Error               0          10d
test-28114121-cvd2m            0/1     Error               0          10d
test-28114121-b6gmd            0/1     Error               0          10d
test-28114121-knpkw            0/1     Error               0          10d
```
This will return in order of execution. We can inspect some logs:
```bash
$ kubectl -n restate logs test-28114121-6pkfw 
{"command": "execute", "test_job": "test-28114121", "result": {"code": "ok", "message": "Success"}}
{"command": "verify", "test_job": "test-28114121", "result": {"code":"internal","message":"Uncaught exception for invocation id 0188bfef334072b6a139322ac883b178: Incorrect value for target 117: expected 1, got 0"}}
```
The first Pod failing is somewhat expected though, as it Verifies immediately after the Execute returns, and most likely
some tasks are still running. Let's check the final one:
```bash
$ kubectl -n restate logs test-28114121-knpkw
{"command": "execute", "test_job": "test-28114121", "result": {"code": "ok", "message": "Success"}}
{"command": "verify", "test_job": "test-28114121", "result": {"code":"internal","message":"Uncaught exception for invocation id 0188bff6d81e71859ec7de391e2068fc: Incorrect value for target 148: expected 1, got 0"}}
```

Hmm, seems like whatever happened, it didn't get better after some time. The next step would probably be to run locally;
in fact this issue is caused by a now resolved problem where command trees could sometimes produce deadlocks, and this
was reproducible sometimes locally.

Another key set of information is the Restate and service endpoint logs, but Kubernetes doesn't do a good job of keeping
these around or making them searchable; it just stores a rolling window of recent loglines for the running pods, but we
care about logs from hours ago, relating to very specific invocations. Fortunately we are using fluent-bit to send logs
to AWS OpenSearch.

## OpenSearch logs
To use OpenSearch, go to https://d-99671f0c4b.awsapps.com/start#/ and click 'Opensearch service' to log in.
In the left menu bar, go to Dashboards and then to the Long Running Test dashboard. This will show logs for all
the components in EKS that relate to the tests. By default this is focussed on the `restate` namespace, but you can change
this to `restate-chaos`

All logs should be indexed with the field `test_job` which will match the job name, which is also the test seed. This field
is extracted via lua code running in the fluent bit log capture pods - it is extracted out of the service key which is emitted
in all sdk loglines and many runtime ones. By filtering for this field using `+Add filter`, a good picture of what happened
and when can be determined. Armed with much more specific timestamps, we can also search for all logs in that time,
and try and spot any other errors that might not have had the right log metadata to contain the `test_job` field.

The logs will rarely be a good substitute for a local reproduction, but trying to spot error messages can be a useful hint.

## Grafana dashboard
We have a dashboard at https://g-3236efe954.grafana-workspace.eu-central-1.amazonaws.com/d/l1GdA1yVk/long-running-verification-tests?orgId=1
which tracks the memory and cpu usage of the verification pods and the restate pod. This can be very helpful in discovering
performance degradations and memory leaks.

This grafana instance is managed by AWS, and is backed by an AWS managed Prometheus instance. A prometheus server in
the cluster scrapes metrics from a `node-exporter` on each node, and these are then sent to the AWS Prometheus cluster.

## #dev-alerts
There is a slack channel #dev-alerts which uses Prometheus metrics to alert whenever there are Job failures in either
the `restate` or `restate-chaos` clusters. As the most recent 10 failed Jobs are always kept around, these alerts will
not resolve until a failing Job is deleted - which would ideally be done after the issue is triaged.

We should expect that the `restate` cluster is vary stable, and failures should be taken very seriously. Failures
in the `restate-chaos` cluster are somewhat harder to debug and are probably lower priority at the moment.

## Database snapshots
You can pull down all the db files with the following command:
```bash
kubectl cp restate-0:/target/db ./target/db
```

However, sometimes you might want to store the whole EBS volume for later analysis. This can be done with a script in
the kube-manifests repo:
```bash
kube-manifests/scripts/clone.sh -n restate-chaos
```
This will create an EBS snapshot of the current state of the Restate storage volume. The snapshot will persist until
the VolumeSnapshot object that this script creates is deleted.

To attach a busybox pod to a snapshot, use the following command:
```bash
kube-manifests/scripts/attach-snapshot.sh -n restate-chaos -s $SNAPSHOT_NAME
```
While that command is running, you can exec into the pod and inspect the contexts, or copy down files as usual:
```bash
kubectl -n restate-chaos exec -it $SNAPSHOT_NAME -- sh
kubectl -n restate-chaos cp $SNAPSHOT_NAME:/target/db ./target/db 
```

## Wiping the database
On upgrades or when there is an irrecoverable failure, you may choose to wipe the storage of a given cluster:
```bash
kube-manifests/scripts/wipe.sh -n restate-chaos
```
This script automatically takes a snapshot of the existing database before deleting anything.
