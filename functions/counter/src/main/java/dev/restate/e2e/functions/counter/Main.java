package dev.restate.e2e.functions.counter;

import dev.restate.sdk.vertx.RestateHttpEndpointBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {

  public static void main(String[] args) {
    RestateHttpEndpointBuilder.builder(Vertx.vertx(new VertxOptions().setWorkerPoolSize(100)))
        .withService(new CounterService())
        .withService(new SingletonCounterService())
        .withService(new NoopService())
        .buildAndListen();
  }
}
