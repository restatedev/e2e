// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.SharedWorkflowContext;
import dev.restate.sdk.WorkflowContext;
import dev.restate.sdk.common.DurablePromiseKey;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.util.Optional;

public class WorkflowAPIBlockAndWaitImpl implements WorkflowAPIBlockAndWait {

  private static final DurablePromiseKey<String> MY_DURABLE_PROMISE =
      DurablePromiseKey.of("durable-promise", JsonSerdes.STRING);
  private static final StateKey<String> MY_STATE = StateKey.of("my-state", JsonSerdes.STRING);

  @Override
  public String run(WorkflowContext context, String input) {
    context.set(MY_STATE, input);

    // Wait on unblock
    String output = context.promise(MY_DURABLE_PROMISE).awaitable().await();

    if (!context.promise(MY_DURABLE_PROMISE).peek().isReady()) {
      throw new TerminalException("Durable promise should be completed");
    }

    return output;
  }

  @Override
  public void unblock(SharedWorkflowContext context, String output) {
    context.promiseHandle(MY_DURABLE_PROMISE).resolve(output);
  }

  @Override
  public Optional<String> getState(SharedWorkflowContext context) {
    return context.get(MY_STATE);
  }
}
