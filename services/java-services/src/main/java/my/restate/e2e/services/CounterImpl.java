// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CounterImpl implements Counter {

  private static final Logger logger = LogManager.getLogger(CounterImpl.class);

  private static final StateKey<Long> COUNTER_KEY = StateKey.of("counter", CoreSerdes.JSON_LONG);

  @Override
  public void add(ObjectContext ctx, long value) {
    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Old counter value: {}", counter);

    counter += value;
    ctx.set(COUNTER_KEY, counter);

    logger.info("New counter value: {}", counter);
  }

  @Override
  public void reset(ObjectContext ctx) {
    logger.info("Counter cleaned up");
    ctx.clear(COUNTER_KEY);
  }

  @Override
  public void addThenFail(ObjectContext ctx, long value) {
    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Old counter value: {}", counter);

    counter += value;
    ctx.set(COUNTER_KEY, counter);

    logger.info("New counter value: {}", counter);

    throw new TerminalException(TerminalException.Code.INTERNAL, ctx.key());
  }

  @Override
  public long get(ObjectContext ctx) {
    long counter = ctx.get(COUNTER_KEY).orElse(0L);
    logger.info("Get counter value: {}", counter);
    return counter;
  }

  @Override
  public CounterUpdateResponse getAndAdd(ObjectContext ctx, long value) {
    long oldCount = ctx.get(COUNTER_KEY).orElse(0L);
    long newCount = oldCount + value;
    ctx.set(COUNTER_KEY, newCount);

    logger.info("Old counter value: {}", oldCount);
    logger.info("New counter value: {}", newCount);

    return new CounterUpdateResponse(oldCount, newCount);
  }

  @Override
  public void infiniteIncrementLoop(ObjectContext context) {
    throw new UnsupportedOperationException();
  }
}
