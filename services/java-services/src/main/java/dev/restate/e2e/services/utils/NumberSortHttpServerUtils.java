package dev.restate.e2e.services.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.generated.core.AwakeableIdentifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;

public class NumberSortHttpServerUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static HttpResponse<Void> sendSortNumbersRequest(
      AwakeableIdentifier replyId, List<Integer> numbers) throws Exception {
    return HttpClient.newHttpClient()
        .send(prepareRequest(replyId, numbers), BodyHandlers.discarding());
  }

  private static HttpRequest prepareRequest(AwakeableIdentifier replyId, List<Integer> numbers)
      throws URISyntaxException, JsonProcessingException {
    return HttpRequest.newBuilder()
        .uri(new URI(System.getenv("HTTP_SERVER_ADDRESS")))
        .header("x-reply-id", Base64.getUrlEncoder().encodeToString(replyId.toByteArray()))
        .PUT(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(numbers)))
        .build();
  }
}
