package dev.restate.e2e.testing.externalhttpserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.restate.e2e.services.externalcall.ReplierGrpc;
import dev.restate.e2e.services.externalcall.ReplierProto.Reply;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
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

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final Logger logger = LogManager.getLogger(Main.class);

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
  public void handle(HttpExchange httpExchange) throws IOException {
    try {
      logger.info("Got a new request with headers " + httpExchange.getRequestHeaders());

      byte[] replyId =
          Base64.getUrlDecoder().decode(httpExchange.getRequestHeaders().getFirst("x-reply-id"));

      List<Integer> inputIntegers =
          new ArrayList<>(
              objectMapper.readValue(httpExchange.getRequestBody(), new TypeReference<>() {}));
      inputIntegers.sort(Integer::compareTo);
      String outputBody = objectMapper.writeValueAsString(inputIntegers);

      logger.info("Output list of numbers is: " + outputBody);

      ReplierGrpc.ReplierBlockingStub replierStub =
          ReplierGrpc.newBlockingStub(
              NettyChannelBuilder.forAddress("runtime", 9090).usePlaintext().build());

      Empty ignored =
          replierStub.replyToRandomNumberListGenerator(
              Reply.newBuilder()
                  .setReplyIdentifier(ByteString.copyFrom(replyId))
                  .setPayload(ByteString.copyFromUtf8(outputBody))
                  .build());

      logger.info("Replier stub invoked and response received");

      httpExchange.sendResponseHeaders(200, -1);
      httpExchange.getResponseBody().close();

      logger.info("Response sent");
    } catch (Exception e) {
      logger.error("Error occurred while processing the request", e);
      throw e;
    }
  }
}
