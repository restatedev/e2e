// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.SharedWorkflowContext;
import dev.restate.sdk.WorkflowContext;
import dev.restate.sdk.common.DurablePromiseKey;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.util.Optional;

public class WorkflowAPIBlockAndWaitImpl implements WorkflowAPIBlockAndWait {

  private static final DurablePromiseKey<String> MY_DURABLE_PROMISE =
      DurablePromiseKey.string("durable-promise");
  private static final StateKey<String> MY_STATE = StateKey.string("my-state");

  @Override
  public String run(WorkflowContext context, String input) {
    context.set(MY_STATE, input);

    // Wait on unblock
    String output = context.durablePromise(MY_DURABLE_PROMISE).awaitable().await();

    if (!context.durablePromise(MY_DURABLE_PROMISE).isCompleted()) {
      throw new TerminalException("Durable promise should be completed");
    }
    if (context.durablePromise(MY_DURABLE_PROMISE).peek().isEmpty()) {
      throw new TerminalException("Durable promise should be completed");
    }

    return output;
  }

  @Override
  public void unblock(SharedWorkflowContext context, String output) {
    context.durablePromiseHandle(MY_DURABLE_PROMISE).resolve(output);
  }

  @Override
  public Optional<String> getState(SharedWorkflowContext context) {
    return context.get(MY_STATE);
  }
}
