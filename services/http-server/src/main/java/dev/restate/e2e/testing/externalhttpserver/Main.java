// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.testing.externalhttpserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.restate.sdk.client.Client;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The goal of this server is to emulate an external system performing an operation and waking up a
 * function using the asyncCall/reply feature.
 */
public class Main implements HttpHandler {

  private static final Logger logger = LogManager.getLogger(Main.class);
  private static final Serde<List<Integer>> LIST_SERDE = JacksonSerdes.of(new TypeReference<>() {});

  private final Client ingressClient =
      Client.connect(Objects.requireNonNull(System.getenv("RESTATE_URI")));

  public static void main(String[] args) throws IOException {
    HttpServer server =
        HttpServer.create(
            new InetSocketAddress(
                Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(8080)),
            0);
    server.createContext("/", new Main());
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
    logger.info("Server started: " + server.getAddress());
  }

  @Override
  public void handle(HttpExchange httpExchange) {
    try {
      String replyId = httpExchange.getRequestHeaders().getFirst("x-reply-id");
      logger.info("Got a new request with reply id " + replyId);

      List<Integer> inputIntegers =
          new ArrayList<>(LIST_SERDE.deserialize(httpExchange.getRequestBody().readAllBytes()));
      inputIntegers.sort(Integer::compareTo);
      logger.info("Output list of numbers is: " + inputIntegers);

      // Resolve awakeable
      logger.info("Sending body: " + inputIntegers);

      // Awakeable handle
      ingressClient.awakeableHandle(replyId).resolve(LIST_SERDE, inputIntegers);

      logger.info("Awakeable resolved");

      httpExchange.sendResponseHeaders(200, -1);
      httpExchange.getResponseBody().close();

      logger.info("Response sent");
    } catch (Throwable e) {
      logger.error("Error occurred while processing the request", e);
      throw new RuntimeException(e);
    }
  }
}
