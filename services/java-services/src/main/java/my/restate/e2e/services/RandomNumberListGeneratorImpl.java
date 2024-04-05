// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.restate.sdk.Awakeable;
import dev.restate.sdk.Context;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import my.restate.e2e.services.utils.NumberSortHttpServerUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomNumberListGeneratorImpl implements RandomNumberListGenerator {

  private static final Logger LOG = LogManager.getLogger(RandomNumberListGeneratorImpl.class);

  private static final Serde<List<Integer>> INT_LIST_SERDE =
      JacksonSerdes.of(new TypeReference<>() {});

  @Override
  public List<Integer> generateNumbers(Context ctx, int itemsToGenerate) {
    LOG.info("Received request " + itemsToGenerate);

    List<Integer> numbers = new ArrayList<>(itemsToGenerate);
    Random random = new Random();

    for (int i = 0; i < itemsToGenerate; i++) {
      numbers.add(random.nextInt());
    }

    Awakeable<List<Integer>> awakeable = ctx.awakeable(INT_LIST_SERDE);

    ctx.run(
        () -> {
          try {
            NumberSortHttpServerUtils.sendSortNumbersRequest(awakeable.id(), numbers);
          } catch (Exception e) {
            throw new RuntimeException(
                "Something went wrong while trying to invoking the external http server", e);
          }
        });

    return awakeable.await();
  }
}
