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

const ProxyCounterServiceFQN = "ProxyCounter";
const Counter: counterApi = { path: "Counter" };

REGISTRY.add({
  fqdn: ProxyCounterServiceFQN,
  binder: (e) => e.service(service),
});

const service = restate.service(ProxyCounterServiceFQN, {
  async addInBackground(
    ctx: restate.Context,
    request: { counterName: string; value: number }
  ) {
    ctx.console.log("addInBackground " + JSON.stringify(request));
    ctx.objectSend(Counter, request.counterName).add(request.value);
  },
});
