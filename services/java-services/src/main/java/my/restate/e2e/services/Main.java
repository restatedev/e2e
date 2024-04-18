// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import java.util.Objects;

public class Main {

  public static void main(String[] args) {
    String env =
        Objects.requireNonNull(
            System.getenv("SERVICES"),
            "SERVICES env variable needs to specify which service to run.");

    RestateHttpEndpointBuilder restateHttpEndpointBuilder = RestateHttpEndpointBuilder.builder();
    for (String svc : env.split(",")) {
      String fqsn = svc.trim();
      switch (fqsn) {
        case AwakeableHolderClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new AwakeableHolderImpl());
          break;
        case ListObjectClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new ListObjectImpl());
          break;
        case CounterClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CounterImpl());
          break;
        case VirtualObjectProxyClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new VirtualObjectProxyImpl());
          break;
        case ProxyCounterClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new ProxyCounterImpl());
          break;
        case FailingClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new FailingImpl());
          break;
        case RandomNumberListGeneratorClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new RandomNumberListGeneratorImpl());
          break;
        case NonDeterministicClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new NonDeterministicImpl());
          break;
        case SideEffectClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new SideEffectImpl());
          break;
        case UpgradeTestClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new UpgradeTestImpl());
          break;
        case EventHandlerClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new EventHandlerImpl());
          break;
        case MapObjectClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new MapObjectImpl());
          break;
        case WorkflowAPIBlockAndWaitClient.WORKFLOW_NAME:
          restateHttpEndpointBuilder.bind(new WorkflowAPIBlockAndWaitImpl());
          break;
        case CoordinatorClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CoordinatorImpl());
          break;
        case ReceiverClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new ReceiverImpl());
          break;
        case CancelTestRunnerClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CancelTestImpl.RunnerImpl());
          break;
        case CancelTestBlockingServiceClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CancelTestImpl.BlockingService());
          break;
        case KillTestRunnerClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new KillTestImpl.RunnerImpl());
          break;
        case KillTestSingletonClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new KillTestImpl.SingletonImpl());
          break;
        case HeadersPassThroughTestClient.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new HeadersPassThroughTestImpl());
          break;
      }
    }

    restateHttpEndpointBuilder.buildAndListen();
  }
}
