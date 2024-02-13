// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

export const HandlerAPIEchoTestFQN = "handlerapi.HandlerAPIEchoTest";

const COUNTER_KEY = "counter";

// These two handlers just test the correct propagation of the input message in the output
export const HandlerAPIEchoRouter = restate.router({
  echo: (_ctx: restate.Context, msg: any): Promise<any> => {
    return msg;
  },
  echoEcho: async (ctx: restate.Context, msg: any): Promise<any> => {
    return ctx.rpc(handlerApi).echo(msg);
  },
});

const handlerApi: restate.ServiceApi<typeof HandlerAPIEchoRouter> = {
  path: HandlerAPIEchoTestFQN,
};

// -- Counter service

export const CounterHandlerAPIFQN = "handlerapi.Counter";

export const CounterHandlerAPIRouter = restate.keyedRouter({
  get: async (
    ctx: restate.KeyedContext,
    key: string,
    value: unknown
  ): Promise<{ counter: number }> => {
    ctx.console.log(
      "get: " + JSON.stringify(key) + " " + JSON.stringify(value)
    );

    const counter = (await ctx.get<number>(COUNTER_KEY)) || 0;

    return { counter };
  },
  add: async (
    ctx: restate.KeyedContext,
    key: string,
    value: { value: number }
  ): Promise<{ newCounter: number; oldCounter: number }> => {
    ctx.console.log(
      "add: " + JSON.stringify(key) + " " + JSON.stringify(value)
    );

    const oldCounter = (await ctx.get<number>(COUNTER_KEY)) || 0;
    const newCounter = oldCounter + 1;
    ctx.set<number>(COUNTER_KEY, newCounter);

    return { oldCounter, newCounter };
  },
  handleEvent: restate.keyedEventHandler(
    async (ctx: restate.KeyedContext, request: restate.Event) => {
      ctx.console.log("handleEvent: " + JSON.stringify(request));

      const value = (await ctx.get<number>(COUNTER_KEY)) || 0;
      const eventValue = parseInt(new TextDecoder().decode(request.body()));
      ctx.console.log("Event value: " + eventValue);
      ctx.set(COUNTER_KEY, value + eventValue);
    }
  ),
});

export const CounterAPI: restate.ServiceApi<typeof CounterHandlerAPIRouter> = {
  path: "handlerapi.Counter",
};
