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

const ID_KEY = "id";

const AwakeableHolderServiceFQN = "AwakeableHolder";

type Ctx = restate.ObjectContext;

REGISTRY.add({
  fqdn: AwakeableHolderServiceFQN,
  binder: (e) => e.object(AwakeableHolderServiceFQN, service),
});

const service = restate.object({
  async hold(ctx: restate.ObjectContext, id: string) {
    ctx.set(ID_KEY, id);
  },

  async hasAwakeable(ctx: Ctx) {
    return (await ctx.get<string>(ID_KEY)) !== null;
  },

  async unlock(ctx: Ctx, request: string) {
    const id = await ctx.get<string>(ID_KEY);
    if (id === null || id === undefined) {
      throw new Error("No awakeable registered");
    }
    ctx.resolveAwakeable(id, request);
    ctx.clear(ID_KEY);
  },
});

export const awakeableHolderApi = restate.objectApi(
  AwakeableHolderServiceFQN,
  service
);
