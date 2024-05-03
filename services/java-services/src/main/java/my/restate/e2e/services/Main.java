// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier;
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
        case AwakeableHolderDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new AwakeableHolderImpl());
          break;
        case ListObjectDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new ListObjectImpl());
          break;
        case CounterDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CounterImpl());
          break;
        case VirtualObjectProxyDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new VirtualObjectProxyImpl());
          break;
        case ProxyCounterDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new ProxyCounterImpl());
          break;
        case FailingDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new FailingImpl());
          break;
        case RandomNumberListGeneratorDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new RandomNumberListGeneratorImpl());
          break;
        case NonDeterministicDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new NonDeterministicImpl());
          break;
        case SideEffectDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new SideEffectImpl());
          break;
        case UpgradeTestDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new UpgradeTestImpl());
          break;
        case EventHandlerDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new EventHandlerImpl());
          break;
        case MapObjectDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new MapObjectImpl());
          break;
        case WorkflowAPIBlockAndWaitClient.WORKFLOW_NAME:
          restateHttpEndpointBuilder.bind(new WorkflowAPIBlockAndWaitImpl());
          break;
        case CoordinatorDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CoordinatorImpl());
          break;
        case ReceiverDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new ReceiverImpl());
          break;
        case CancelTestRunnerDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CancelTestImpl.RunnerImpl());
          break;
        case CancelTestBlockingServiceDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new CancelTestImpl.BlockingService());
          break;
        case KillTestRunnerDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new KillTestImpl.RunnerImpl());
          break;
        case KillTestSingletonDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new KillTestImpl.SingletonImpl());
          break;
        case HeadersPassThroughTestDefinitions.SERVICE_NAME:
          restateHttpEndpointBuilder.bind(new HeadersPassThroughTestImpl());
          break;
      }
    }

    String requestSigningKey = System.getenv("E2E_REQUEST_SIGNING");
    if (requestSigningKey != null) {
      restateHttpEndpointBuilder.withRequestIdentityVerifier(
          RestateRequestIdentityVerifier.fromKey(requestSigningKey));
    }

    restateHttpEndpointBuilder.buildAndListen();
  }
}
