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
        case AwakeableHolderClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new AwakeableHolderImpl());
          break;
        case ListObjectClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new ListObjectImpl());
          break;
        case CounterClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new CounterImpl());
          break;
        case VirtualObjectProxyClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new VirtualObjectProxyImpl());
          break;
        case ProxyCounterClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new ProxyCounterImpl());
          break;
        case FailingClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new FailingImpl());
          break;
        case RandomNumberListGeneratorClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new RandomNumberListGeneratorImpl());
          break;
        case NonDeterministicClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new NonDeterministicImpl());
          break;
        case SideEffectClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new SideEffectImpl());
          break;
        case UpgradeTestClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new UpgradeTestImpl());
          break;
        case EventHandlerClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new EventHandlerImpl());
          break;
        case MapObjectClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new MapObjectImpl());
          break;
        case WorkflowAPIBlockAndWaitClient.WORKFLOW_NAME:
          restateHttpEndpointBuilder.with(new WorkflowAPIBlockAndWaitImpl());
          break;
        case CoordinatorClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new CoordinatorImpl());
          break;
        case ReceiverClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new ReceiverImpl());
          break;
        case CancelTestRunnerClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new CancelTestImpl.RunnerImpl());
          break;
        case CancelTestBlockingServiceClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new CancelTestImpl.BlockingService());
          break;
        case KillTestRunnerClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new KillTestImpl.RunnerImpl());
          break;
        case KillTestSingletonClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new KillTestImpl.SingletonImpl());
          break;
        case HeadersPassThroughTestClient.COMPONENT_NAME:
          restateHttpEndpointBuilder.with(new HeadersPassThroughTestImpl());
          break;
      }
    }

    restateHttpEndpointBuilder.buildAndListen();
  }
}
