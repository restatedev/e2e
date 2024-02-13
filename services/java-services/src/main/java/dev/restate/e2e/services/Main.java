// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services;

import dev.restate.e2e.services.canceltest.BlockingService;
import dev.restate.e2e.services.canceltest.BlockingServiceGrpc;
import dev.restate.e2e.services.canceltest.CancelTestService;
import dev.restate.e2e.services.canceltest.CancelTestServiceGrpc;
import dev.restate.e2e.services.collections.ListService;
import dev.restate.e2e.services.collections.MapService;
import dev.restate.e2e.services.collections.list.ListServiceGrpc;
import dev.restate.e2e.services.collections.map.MapServiceGrpc;
import dev.restate.e2e.services.coordinator.CoordinatorGrpc;
import dev.restate.e2e.services.coordinator.CoordinatorService;
import dev.restate.e2e.services.coordinator.ReceiverService;
import dev.restate.e2e.services.counter.*;
import dev.restate.e2e.services.counter.CounterService;
import dev.restate.e2e.services.counter.SingletonCounterService;
import dev.restate.e2e.services.errors.FailingService;
import dev.restate.e2e.services.errors.FailingServiceGrpc;
import dev.restate.e2e.services.eventhandler.EventHandlerGrpc;
import dev.restate.e2e.services.eventhandler.EventHandlerService;
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorGrpc;
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorService;
import dev.restate.e2e.services.nondeterminism.NonDeterministicService;
import dev.restate.e2e.services.nondeterminism.NonDeterministicServiceGrpc;
import dev.restate.e2e.services.receiver.ReceiverGrpc;
import dev.restate.e2e.services.sideeffect.SideEffectGrpc;
import dev.restate.e2e.services.sideeffect.SideEffectService;
import dev.restate.e2e.services.singletoncounter.SingletonCounterGrpc;
import dev.restate.e2e.services.upgradetest.UpgradeTestService;
import dev.restate.e2e.services.upgradetest.UpgradeTestServiceGrpc;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;
import java.util.Objects;
import my.restate.e2e.services.WorkflowAPIBlockAndWaitImpl;
import my.restate.e2e.services.WorkflowAPIBlockAndWaitServiceAdapter;

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
        case ListServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new ListService());
          break;
        case CoordinatorGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new CoordinatorService());
          break;
        case ReceiverGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new ReceiverService());
          break;
        case CounterGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new CounterService());
          break;
        case ProxyCounterGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new ProxyCounterService());
          break;
        case SingletonCounterGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new SingletonCounterService());
          break;
        case FailingServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new FailingService());
          break;
        case RandomNumberListGeneratorGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new RandomNumberListGeneratorService());
          break;
        case NonDeterministicServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new NonDeterministicService());
          break;
        case SideEffectGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new SideEffectService());
          break;
        case UpgradeTestServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new UpgradeTestService());
          break;
        case EventHandlerGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new EventHandlerService());
          break;
        case CancelTestServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new CancelTestService());
          break;
        case BlockingServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new BlockingService());
        case MapServiceGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new MapService());
          break;
        case WorkflowAPIBlockAndWaitServiceAdapter.SERVICE_NAME:
          restateHttpEndpointBuilder.with(
              new WorkflowAPIBlockAndWaitImpl(), new WorkflowAPIBlockAndWaitServiceAdapter());
          break;
      }
    }

    restateHttpEndpointBuilder.buildAndListen();
  }
}
