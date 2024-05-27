// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NonDeterministicImpl implements NonDeterministic {

  private final Map<String, Integer> invocationCounts = new ConcurrentHashMap<>();
  private final StateKey<String> STATE_A = StateKey.of("a", JsonSerdes.STRING);
  private final StateKey<String> STATE_B = StateKey.of("b", JsonSerdes.STRING);

  @Override
  public void leftSleepRightCall(ObjectContext context) {
    if (doLeftAction(context)) {
      context.sleep(Duration.ofMillis(100));
    } else {
      CounterClient.fromContext(context, "abc").get().await();
    }
    incrementCounter(context);
  }

  @Override
  public void callDifferentMethod(ObjectContext context) {
    if (doLeftAction(context)) {
      CounterClient.fromContext(context, "abc").get().await();
    } else {
      CounterClient.fromContext(context, "abc").reset().await();
    }
    incrementCounter(context);
  }

  @Override
  public void backgroundInvokeWithDifferentTargets(ObjectContext context) {
    if (doLeftAction(context)) {
      CounterClient.fromContext(context, "abc").send().get();
    } else {
      CounterClient.fromContext(context, "abc").send().reset();
    }
    context.sleep(Duration.ofMillis(100));
    incrementCounter(context);
  }

  @Override
  public void setDifferentKey(ObjectContext context) {
    if (doLeftAction(context)) {
      context.set(STATE_A, "my-state");
    } else {
      context.set(STATE_B, "my-state");
    }
    context.sleep(Duration.ofMillis(100));
    incrementCounter(context);
  }

  private boolean doLeftAction(ObjectContext context) {
    return invocationCounts.merge(context.key(), 1, Integer::sum) % 2 == 1;
  }

  private void incrementCounter(ObjectContext context) {
    CounterClient.fromContext(context, context.key()).send().add(1);
  }
}
