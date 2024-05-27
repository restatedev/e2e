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
import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.time.Duration;

public class CancelTestImpl {

  public static class RunnerImpl implements CancelTest.Runner {
    private static final StateKey<Boolean> CANCELED_STATE =
        StateKey.of("canceled", JsonSerdes.BOOLEAN);

    @Override
    public void startTest(ObjectContext context, CancelTest.BlockingOperation operation) {
      var client = CancelTestBlockingServiceClient.fromContext(context, "");

      try {
        client.block(operation).await();
      } catch (TerminalException e) {
        if (e.getCode() == TerminalException.CANCELLED_CODE) {
          context.set(CANCELED_STATE, true);
        } else {
          throw e;
        }
      }
    }

    @Override
    public boolean verifyTest(ObjectContext context) throws TerminalException {
      return context.get(CANCELED_STATE).orElse(false);
    }
  }

  public static class BlockingService implements CancelTest.BlockingService {
    @Override
    public void block(ObjectContext context, CancelTest.BlockingOperation operation) {
      final var self = CancelTestBlockingServiceClient.fromContext(context, "");
      final var client = AwakeableHolderClient.fromContext(context, "cancel");

      Awakeable<String> awakeable = context.awakeable(JsonSerdes.STRING);
      client.hold(awakeable.id()).await();
      awakeable.await();

      switch (operation) {
        case CALL:
          self.block(operation).await();
          break;
        case SLEEP:
          context.sleep(Duration.ofDays(1024));
          break;
        case AWAKEABLE:
          Awakeable<String> uncompletable = context.awakeable(JsonSerdes.STRING);
          uncompletable.await();
          break;
        default:
          throw new IllegalArgumentException("Unknown operation: " + operation);
      }
    }

    @Override
    public void isUnlocked(ObjectContext context) throws TerminalException {
      // no-op
    }
  }
}
