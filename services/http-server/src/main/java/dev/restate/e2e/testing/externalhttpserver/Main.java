package dev.restate.e2e.testing.externalhttpserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The goal of this server is to emulate an external system performing an operation and waking up a
 * function using the asyncCall/reply feature.
 */
public class Main implements HttpHandler {

  private static final Logger logger = LogManager.getLogger(Main.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();
  private final boolean encode_result_as_base64 = System.getenv("ENCODE_RESULT_AS_BASE64") != null;

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
          new ArrayList<>(
              objectMapper.readValue(httpExchange.getRequestBody(), new TypeReference<>() {}));
      inputIntegers.sort(Integer::compareTo);
      logger.info("Output list of numbers is: " + inputIntegers);

      // Resolve awakeable
      ObjectNode resolveAwakeableRequest = objectMapper.createObjectNode();
      resolveAwakeableRequest.set("id", objectMapper.valueToTree(replyId));
      if (encode_result_as_base64) {
        String json_string = objectMapper.valueToTree(inputIntegers).toPrettyString();
        resolveAwakeableRequest.set(
            "bytes_result",
            objectMapper
                .getNodeFactory()
                .textNode(
                    Base64.getEncoder()
                        .encodeToString(json_string.getBytes(StandardCharsets.UTF_8))));
      } else {
        resolveAwakeableRequest.set("json_result", objectMapper.valueToTree(inputIntegers));
      }
      logger.info("Sending body: " + resolveAwakeableRequest.toPrettyString());

      HttpRequest req =
          HttpRequest.newBuilder(URI.create("http://runtime:8080/dev.restate.Awakeables/Resolve"))
              .POST(HttpRequest.BodyPublishers.ofString(resolveAwakeableRequest.toPrettyString()))
              .headers("Content-Type", "application/json")
              .build();
      HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "Unexpected status code " + response.statusCode() + ". Body: " + response.body());
      }

      logger.info(
          "Replier stub invoked and response received. Status code "
              + response.statusCode()
              + ". Body: "
              + response.body());

      httpExchange.sendResponseHeaders(200, -1);
      httpExchange.getResponseBody().close();

      logger.info("Response sent");
    } catch (Throwable e) {
      logger.error("Error occurred while processing the request", e);
      throw new RuntimeException(e);
    }
  }
}
