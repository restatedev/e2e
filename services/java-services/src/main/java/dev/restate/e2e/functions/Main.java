package dev.restate.e2e.functions;

import dev.restate.e2e.functions.collections.ListService;
import dev.restate.e2e.functions.collections.list.ListServiceGrpc;
import dev.restate.e2e.functions.coordinator.CoordinatorGrpc;
import dev.restate.e2e.functions.coordinator.CoordinatorService;
import dev.restate.e2e.functions.coordinator.ReceiverService;
import dev.restate.e2e.functions.counter.*;
import dev.restate.e2e.functions.errors.FailingService;
import dev.restate.e2e.functions.errors.FailingServiceGrpc;
import dev.restate.e2e.functions.externalcall.RandomNumberListGeneratorGrpc;
import dev.restate.e2e.functions.externalcall.RandomNumberListGeneratorService;
import dev.restate.e2e.functions.externalcall.ReplierGrpc;
import dev.restate.e2e.functions.externalcall.ReplierService;
import dev.restate.e2e.functions.receiver.ReceiverGrpc;
import dev.restate.e2e.functions.singletoncounter.SingletonCounterGrpc;
import dev.restate.sdk.vertx.RestateHttpEndpointBuilder;
import io.vertx.core.Vertx;
import java.util.Objects;

public class Main {

  public static void main(String[] args) {
    String env = Objects.requireNonNull(System.getenv("SERVICES"));

    RestateHttpEndpointBuilder restateHttpEndpointBuilder =
        RestateHttpEndpointBuilder.builder(Vertx.vertx());
    for (String service : env.split(",")) {
      switch (service.trim()) {
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
        case NoopGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new NoopService());
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
        case ReplierGrpc.SERVICE_NAME:
          restateHttpEndpointBuilder.withService(new ReplierService());
          break;
      }
    }

    restateHttpEndpointBuilder.buildAndListen();
  }
}
