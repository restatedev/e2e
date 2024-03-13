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
import { counterApi } from "./counter";

const EventHandlerFQN = "EventHandler";

const CounterApi: counterApi = { path: "Counter" };

REGISTRY.add({
  fqdn: EventHandlerFQN,
  binder: (e) => e.object(service),
});

const service = restate.object(EventHandlerFQN, {
  async handle(ctx: restate.ObjectContext, value: number) {
    ctx.objectSend(CounterApi, ctx.key()).add(value);
  },
});
