package dev.restate.e2e.functions.collections;

import dev.restate.sdk.vertx.RestateHttpEndpointBuilder;
import io.vertx.core.Vertx;

public class Main {

  public static void main(String[] args) {
    RestateHttpEndpointBuilder.builder(Vertx.vertx())
        .withService(new ListService())
        .buildAndListen();
  }
}
