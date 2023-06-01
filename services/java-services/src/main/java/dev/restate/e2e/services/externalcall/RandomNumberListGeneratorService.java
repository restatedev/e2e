package dev.restate.e2e.services.externalcall;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorProto.GenerateNumbersRequest;
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorProto.GenerateNumbersResponse;
import dev.restate.e2e.services.utils.NumberSortHttpServerUtils;
import dev.restate.sdk.blocking.Awakeable;
import dev.restate.sdk.blocking.RestateBlockingService;
import dev.restate.sdk.core.TypeTag;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomNumberListGeneratorService
    extends RandomNumberListGeneratorGrpc.RandomNumberListGeneratorImplBase
    implements RestateBlockingService {

  private static final Logger LOG = LogManager.getLogger(RandomNumberListGeneratorService.class);

  private static final ObjectMapper jsonObjectMapper = new ObjectMapper();
  private static final TypeReference<List<Integer>> INT_LIST_TYPE_REF = new TypeReference<>() {};
  private static final TypeTag<List<Integer>> INT_LIST_TYPE_TAG =
      TypeTag.using(
          value -> {
            try {
              return jsonObjectMapper.writeValueAsBytes(value);
            } catch (JsonProcessingException e) {
              throw new RuntimeException(e);
            }
          },
          b -> {
            try {
              return jsonObjectMapper.readValue(b, INT_LIST_TYPE_REF);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });

  @Override
  public void generateNumbers(
      GenerateNumbersRequest request, StreamObserver<GenerateNumbersResponse> responseObserver) {
    LOG.info("Received request " + request);

    List<Integer> numbers = new ArrayList<>(request.getItemsNumber());
    Random random = new Random();

    for (int i = 0; i < request.getItemsNumber(); i++) {
      numbers.add(random.nextInt());
    }

    var ctx = restateContext();

    Awakeable<List<Integer>> awakeable = ctx.awakeable(INT_LIST_TYPE_TAG);

    ctx.sideEffect(
        () -> {
          try {
            NumberSortHttpServerUtils.sendSortNumbersRequest(awakeable.id(), numbers);
          } catch (Exception e) {
            throw new RuntimeException(
                "Something went wrong while trying to invoking the external http server", e);
          }
        });

    List<Integer> sortedNumbers = awakeable.await();

    responseObserver.onNext(
        GenerateNumbersResponse.newBuilder().addAllNumbers(sortedNumbers).build());
    responseObserver.onCompleted();
  }
}
