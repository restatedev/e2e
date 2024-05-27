// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.Awaitable;
import dev.restate.sdk.Context;
import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.common.Serde;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoordinatorImpl implements Coordinator {
  private static final Logger LOG = LogManager.getLogger(CoordinatorImpl.class);

  @Override
  public void sleep(Context context, long millisDuration) {
    manyTimers(context, List.of(millisDuration));
  }

  @Override
  public void manyTimers(Context context, List<Long> millisDurations) {
    LOG.info("many timers {}", millisDurations);

    awaitableAll(
        millisDurations.stream()
            .map(d -> context.timer(Duration.ofMillis(d)))
            .collect(Collectors.toList()));
  }

  @Override
  public String proxy(Context context) {
    String key = context.run(JsonSerdes.STRING, () -> UUID.randomUUID().toString());

    var pong = ReceiverClient.fromContext(context, key).ping().await();

    return pong;
  }

  @Override
  public String complex(Context context, CoordinatorComplexRequest complexRequest) {
    LOG.info(
        "Starting complex coordination by sleeping for {} ms",
        complexRequest.getSleepDurationMillis());

    context.sleep(Duration.ofMillis(complexRequest.getSleepDurationMillis()));

    var receiverUUID = context.run(JsonSerdes.STRING, () -> UUID.randomUUID().toString());
    var receiverClient = ReceiverClient.fromContext(context, receiverUUID);

    LOG.info("Send fire and forget call to {}", ReceiverDefinitions.SERVICE_NAME);
    // services should be invoked in the same order they were called. This means that
    // background calls as well as request-response calls have an absolute ordering that is defined
    // by their call order. In this concrete case, setValue is guaranteed to be executed before
    // getValue.
    receiverClient.send().setValue(complexRequest.getRequestValue());

    LOG.info("Get current value from {}", ReceiverDefinitions.SERVICE_NAME);
    var response = receiverClient.getValue().await();

    LOG.info("Finish complex coordination with response value '{}'", response);
    return response;
  }

  @Override
  public boolean timeout(Context context, long millisDuration) {
    var timeoutOccurred = false;

    var awakeable = context.awakeable(Serde.VOID);
    try {
      awakeable.await(Duration.ofMillis(millisDuration));
    } catch (TimeoutException te) {
      timeoutOccurred = true;
    }

    return timeoutOccurred;
  }

  @Override
  public void invokeSequentially(Context context, CoordinatorInvokeSequentiallyRequest request) {
    List<Awaitable<?>> collectedAwaitables = new ArrayList<>();

    var listClient = ListObjectClient.fromContext(context, request.getListName());
    for (int i = 0; i < request.getExecuteAsBackgroundCall().size(); i++) {
      if (request.getExecuteAsBackgroundCall().get(i)) {
        listClient.send().append(String.valueOf(i));
      } else {
        collectedAwaitables.add(listClient.append(String.valueOf(i)));
      }
    }

    awaitableAll(collectedAwaitables);
  }

  private static void awaitableAll(List<Awaitable<?>> awaitables) {
    if (awaitables.size() == 1) {
      awaitables.get(0).await();
    } else if (awaitables.size() == 2) {
      Awaitable.all(awaitables.get(0), awaitables.get(1)).await();
    } else if (awaitables.size() >= 2) {
      Awaitable.all(
              awaitables.get(0),
              awaitables.get(1),
              awaitables.subList(2, awaitables.size()).toArray(Awaitable[]::new))
          .await();
    }
  }
}
