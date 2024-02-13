// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

const MY_STATE = "my-state";
const MY_DURABLE_PROMISE = "durable-promise";

export const WorkflowAPIBlockAndWaitFQN = "WorkflowAPIBlockAndWait";

export const WorkflowAPIBlockAndWait = restate.workflow.workflow(
  WorkflowAPIBlockAndWaitFQN,
  {
    run: async (ctx: restate.workflow.WfContext, params: { input: string }) => {
      ctx.console.log("input: " + JSON.stringify(params));
      ctx.set(MY_STATE, params.input);

      // Wait on unblock
      const p = ctx.promise<string>(MY_DURABLE_PROMISE);
      const output = await p.promise();

      // Check peek works
      ctx.console.assert((await p.peek()) == output);

      return output;
    },

    unblock: async (
      ctx: restate.workflow.SharedWfContext,
      params: { output: string }
    ) => {
      ctx.promise<string>(MY_DURABLE_PROMISE).resolve(params.output);
    },

    getState: async (
      ctx: restate.workflow.SharedWfContext
    ): Promise<string> => {
      return (await ctx.get(MY_STATE)) ?? "(not yet set)";
    },
  }
);
