// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import static java.util.Map.entry;

import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import java.util.Map;
import java.util.function.Supplier;

public class Main {

  private static final Map<String, Supplier<Object>> KNOWN_SERVICES_FACTORIES =
      Map.ofEntries(
          entry(AwakeableHolderDefinitions.SERVICE_NAME, AwakeableHolderImpl::new),
          entry(ListObjectDefinitions.SERVICE_NAME, ListObjectImpl::new),
          entry(CounterDefinitions.SERVICE_NAME, CounterImpl::new),
          entry(VirtualObjectProxyDefinitions.SERVICE_NAME, VirtualObjectProxyImpl::new),
          entry(ProxyCounterDefinitions.SERVICE_NAME, ProxyCounterImpl::new),
          entry(FailingDefinitions.SERVICE_NAME, FailingImpl::new),
          entry(
              RandomNumberListGeneratorDefinitions.SERVICE_NAME,
              RandomNumberListGeneratorImpl::new),
          entry(NonDeterministicDefinitions.SERVICE_NAME, NonDeterministicImpl::new),
          entry(SideEffectDefinitions.SERVICE_NAME, SideEffectImpl::new),
          entry(UpgradeTestDefinitions.SERVICE_NAME, UpgradeTestImpl::new),
          entry(EventHandlerDefinitions.SERVICE_NAME, EventHandlerImpl::new),
          entry(MapObjectDefinitions.SERVICE_NAME, MapObjectImpl::new),
          entry(WorkflowAPIBlockAndWaitDefinitions.SERVICE_NAME, WorkflowAPIBlockAndWaitImpl::new),
          entry(CoordinatorDefinitions.SERVICE_NAME, CoordinatorImpl::new),
          entry(ReceiverDefinitions.SERVICE_NAME, ReceiverImpl::new),
          entry(CancelTestRunnerDefinitions.SERVICE_NAME, CancelTestImpl.RunnerImpl::new),
          entry(
              CancelTestBlockingServiceDefinitions.SERVICE_NAME,
              CancelTestImpl.BlockingService::new),
          entry(KillTestRunnerDefinitions.SERVICE_NAME, KillTestImpl.RunnerImpl::new),
          entry(KillTestSingletonDefinitions.SERVICE_NAME, KillTestImpl.SingletonImpl::new),
          entry(HeadersPassThroughTestDefinitions.SERVICE_NAME, HeadersPassThroughTestImpl::new),
          entry(EchoDefinitions.SERVICE_NAME, EchoImpl::new));

  public static void main(String[] args) {
    String env = System.getenv("SERVICES");
    if (env == null) {
      env = "*";
    }
    RestateHttpEndpointBuilder restateHttpEndpointBuilder = RestateHttpEndpointBuilder.builder();
    if (env.equals("*")) {
      KNOWN_SERVICES_FACTORIES
          .values()
          .forEach(factory -> restateHttpEndpointBuilder.bind(factory.get()));
    } else {
      for (String svc : env.split(",")) {
        String fqsn = svc.trim();
        restateHttpEndpointBuilder.bind(
            KNOWN_SERVICES_FACTORIES
                .getOrDefault(
                    fqsn,
                    () -> {
                      throw new IllegalStateException("Service " + fqsn + " not implemented");
                    })
                .get());
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
