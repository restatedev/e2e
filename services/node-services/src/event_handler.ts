// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";
import { REGISTRY } from "./services";
import { CounterApi } from "./counter";

const EventHandlerFQN = "EventHandler";

const CounterApi: CounterApi = { name: "Counter" };

const o = restate.object({
  name: EventHandlerFQN,
  handlers: {
    async handle(ctx: restate.ObjectContext, value: number) {
      ctx.objectSendClient(CounterApi, ctx.key).add(value);
    },
  },
});

REGISTRY.addObject(o);
