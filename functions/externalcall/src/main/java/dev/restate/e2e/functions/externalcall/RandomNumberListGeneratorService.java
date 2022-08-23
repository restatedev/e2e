package dev.restate.e2e.functions.externalcall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.e2e.functions.utils.NumberSortHttpServerUtils;
import dev.restate.sdk.RestateContext;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
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
            .callback(
                byte[].class,
                replyId -> {
                  try {
                    NumberSortHttpServerUtils.sendSortNumbersRequest(replyId, numbers);
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
}
