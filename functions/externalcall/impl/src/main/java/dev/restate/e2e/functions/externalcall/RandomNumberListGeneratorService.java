package dev.restate.e2e.functions.externalcall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.ReplyIdentifier;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomNumberListGeneratorService
    extends RandomNumberListGeneratorGrpc.RandomNumberListGeneratorImplBase {

  private static final Logger LOG = LogManager.getLogger(RandomNumberListGeneratorService.class);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void generateNumbers(
      GenerateNumbersRequest request, StreamObserver<GenerateNumbersResponse> responseObserver) {
    LOG.info("Received request " + request);

    List<Integer> numbers = new ArrayList<>(request.getItemsNumber());
    Random random = new Random();

    for (int i = 0; i < request.getItemsNumber(); i++) {
      numbers.add(random.nextInt());
    }

    byte[] sortedNumbersJson =
        RestateContext.current()
            .asyncCall(
                byte[].class,
                replyId -> {
                  try {
                    sendExternalSortNumbersRequest(
                        replyId, objectMapper.writeValueAsBytes(numbers));
                  } catch (Exception e) {
                    throw new RuntimeException(
                        "Something went wrong while trying to invoking the external http server",
                        e);
                  }
                })
            .await();

    List<Integer> sortedNumbers = null;
    try {
      sortedNumbers = objectMapper.readValue(sortedNumbersJson, new TypeReference<>() {});
    } catch (IOException e) {
      throw new RuntimeException("Cannot deserialize output of async call", e);
    }

    responseObserver.onNext(
        GenerateNumbersResponse.newBuilder().addAllNumbers(sortedNumbers).build());
    responseObserver.onCompleted();
  }

  private void sendExternalSortNumbersRequest(ReplyIdentifier replyId, byte[] serializedNumbers)
      throws Exception {
    HttpClient client = HttpClient.newHttpClient();

    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(new URI(System.getenv("HTTP_SERVER_ADDRESS")))
            .header("x-reply-id", Base64.getUrlEncoder().encodeToString(replyId.toBytes()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(serializedNumbers))
            .build();

    client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
  }
}
