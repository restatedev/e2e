package dev.restate.e2e.functions.externalcall;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.core.serde.jackson.JacksonSerde;
import dev.restate.sdk.vertx.RestateHttpEndpointBuilder;
import io.vertx.core.Vertx;
import java.io.IOException;

public class Main {

  public static void main(String[] args) throws IOException, InterruptedException {
    RestateHttpEndpointBuilder.builder(Vertx.vertx())
        .withService(new RandomNumberListGeneratorService())
        .withService(new ReplierService())
        // Use the default object mapper to use JSON rather than CBOR.
        .withSerde(JacksonSerde.usingMapper(new ObjectMapper()))
        .buildAndListen();
  }
}
