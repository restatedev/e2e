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
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.Workflow;
import java.util.Optional;

@Workflow
public interface WorkflowAPIBlockAndWait {

  @Workflow
  String run(WorkflowContext context, String input);

  @Shared
  void unblock(SharedWorkflowContext context, String output);

  @Shared
  Optional<String> getState(SharedWorkflowContext context);
}
