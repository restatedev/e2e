// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.Awakeable;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.TerminalException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import my.restate.e2e.services.utils.NumberSortHttpServerUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FailingImpl implements Failing {

  private static final Logger LOG = LogManager.getLogger(FailingImpl.class);

  private final AtomicInteger eventualSuccessCalls = new AtomicInteger(0);
  private final AtomicInteger eventualSuccessSideEffectCalls = new AtomicInteger(0);

  @Override
  public void terminallyFailingCall(ObjectContext context, String errorMessage) {
    LOG.info("Invoked fail");

    throw new TerminalException(TerminalException.Code.INTERNAL, errorMessage);
  }

  @Override
  public String callTerminallyFailingCall(ObjectContext ctx, String errorMessage) {
    LOG.info("Invoked failAndHandle");

    FailingClient.fromContext(ctx, ctx.random().nextUUID().toString())
        .terminallyFailingCall(errorMessage)
        .await();

    throw new IllegalStateException("This should be unreachable");
  }

  @Override
  public String invokeExternalAndHandleFailure(ObjectContext ctx) {
    LOG.info("Invoked invokeExternalAndHandleFailure");

    String finalMessage = "begin";

    Awakeable<byte[]> awakeable = ctx.awakeable(CoreSerdes.RAW);

    try {
      ctx.sideEffect(
          () -> {
            try {
              NumberSortHttpServerUtils.sendSortNumbersRequest(
                  awakeable.id(), Arrays.asList(3, 2, 1));
            } catch (Exception e) {
              throw new RuntimeException(
                  "Something went wrong while trying to invoke the external http server", e);
            }
            throw new TerminalException(TerminalException.Code.INTERNAL, "external_call");
          });

      awakeable.await();
    } catch (TerminalException e) {
      finalMessage = finalMessage + ":" + e.getMessage();
    }

    try {
      FailingClient.fromContext(ctx, ctx.random().nextUUID().toString())
          .terminallyFailingCall("internal_call")
          .await();

    } catch (TerminalException e) {
      finalMessage = finalMessage + ":" + e.getMessage();
    }

    return finalMessage;
  }

  @Override
  public int failingCallWithEventualSuccess(ObjectContext context) {
    final int currentAttempt = this.eventualSuccessCalls.incrementAndGet();

    if (currentAttempt >= 4) {
      this.eventualSuccessCalls.set(0);
      return currentAttempt;
    } else {
      throw new IllegalArgumentException("Failed at attempt: " + currentAttempt);
    }
  }

  @Override
  public int failingSideEffectWithEventualSuccess(ObjectContext context) {
    final int successAttempt =
        context.sideEffect(
            CoreSerdes.JSON_INT,
            () -> {
              final int currentAttempt = this.eventualSuccessSideEffectCalls.incrementAndGet();

              if (currentAttempt >= 4) {
                this.eventualSuccessSideEffectCalls.set(0);
                return currentAttempt;
              } else {
                throw new IllegalArgumentException("Failed at attempt: " + currentAttempt);
              }
            });

    return successAttempt;
  }

  @Override
  public void terminallyFailingSideEffect(ObjectContext context, String errorMessage) {
    context.sideEffect(
        () -> {
          throw new TerminalException(TerminalException.Code.INTERNAL, errorMessage);
        });

    throw new IllegalStateException("Should not be reached.");
  }
}
