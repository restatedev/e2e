// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";
import { CommandType, Program } from "./commands";
import { interpreterObjectForLayer, type InterpreterId } from "./interpreter";

/**
 * The following is an auxiliary service that is being called
 * by the interpreter objects
 */

export const serviceInterpreterHelper = restate.service({
  name: "ServiceInterpreterHelper",
  handlers: {
    ping: async () => {},

    echo: async (
      _ctx: restate.Context,
      parameters: string
    ): Promise<string> => {
      return parameters;
    },

    echoLater: async (
      ctx: restate.Context,
      parameter: { sleep: number; parameter: string }
    ): Promise<string> => {
      await ctx.sleep(parameter.sleep);
      return parameter.parameter;
    },

    terminalFailure: async (): Promise<string> => {
      throw new restate.TerminalError(`bye`);
    },

    incrementIndirectly: async (ctx: restate.Context, id: InterpreterId) => {
      const program: Program = {
        commands: [
          {
            kind: CommandType.INCREMENT_STATE_COUNTER,
          },
        ],
      };

      const obj = interpreterObjectForLayer(id.layer);

      ctx.objectSendClient(obj, id.key).interpret(program);
    },

    resolveAwakeable: async (ctx: restate.Context, id: string) => {
      ctx.resolveAwakeable(id, "ok");
    },

    rejectAwakeable: async (ctx: restate.Context, id: string) => {
      ctx.rejectAwakeable(id, "error");
    },

    incrementViaAwakeableDance: async (
      ctx: restate.Context,
      input: { interpreter: InterpreterId; txPromiseId: string }
    ) => {
      //
      // 1. create an awakeable that we will be blocked on
      //
      const { id, promise } = ctx.awakeable<string>();
      //
      // 2. send our awakeable id to the interpreter via txPromise.
      //
      ctx.resolveAwakeable(input.txPromiseId, id);
      //
      // 3. wait for the interpreter resolve us
      //
      await promise;
      //
      // 4. to thank our interpret, let us ask it to inc its state.
      //
      const program: Program = {
        commands: [
          {
            kind: CommandType.INCREMENT_STATE_COUNTER,
          },
        ],
      };

      const obj = interpreterObjectForLayer(input.interpreter.layer);

      ctx.objectSendClient(obj, input.interpreter.key).interpret(program);
    },
  },
});

export type ServiceInterpreterHelper = typeof serviceInterpreterHelper;
