// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.externalcall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorProto.GenerateNumbersRequest;
import dev.restate.e2e.services.externalcall.RandomNumberListGeneratorProto.GenerateNumbersResponse;
import dev.restate.e2e.services.utils.NumberSortHttpServerUtils;
import dev.restate.sdk.Awakeable;
import dev.restate.sdk.UnkeyedContext;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomNumberListGeneratorService
    extends RandomNumberListGeneratorRestate.RandomNumberListGeneratorRestateImplBase {

  private static final Logger LOG = LogManager.getLogger(RandomNumberListGeneratorService.class);

  private static final ObjectMapper jsonObjectMapper = new ObjectMapper();
  private static final Serde<List<Integer>> INT_LIST_SERDE =
      JacksonSerdes.of(jsonObjectMapper, new TypeReference<>() {});

  @Override
  public GenerateNumbersResponse generateNumbers(
      UnkeyedContext ctx, GenerateNumbersRequest request) {
    LOG.info("Received request " + request);

    List<Integer> numbers = new ArrayList<>(request.getItemsNumber());
    Random random = new Random();

    for (int i = 0; i < request.getItemsNumber(); i++) {
      numbers.add(random.nextInt());
    }

    Awakeable<List<Integer>> awakeable = ctx.awakeable(INT_LIST_SERDE);

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

    return GenerateNumbersResponse.newBuilder().addAllNumbers(sortedNumbers).build();
  }
}
