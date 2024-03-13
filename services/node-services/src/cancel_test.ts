// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";
import { awakeableHolderApi } from "./awakeable_holder";
import { REGISTRY } from "./services";

export const CancelTestServiceFQN = "CancelTestRunner";
export const BlockingServiceFQN = "CancelTestBlockingService";
const AwakeableHolder: awakeableHolderApi = { path: "AwakeableHolder"};

REGISTRY.add({
  fqdn: CancelTestServiceFQN,
  binder: (e) => e.object(canceService),
});

REGISTRY.add({
  fqdn: BlockingServiceFQN,
  binder: (e) => e.object(blockingService),
});

enum BlockingOperation {
  CALL = "CALL",
  SLEEP = "SLEEP",
  AWAKEABLE = "AWAKEABLE",
}

const canceService = restate.object(CancelTestServiceFQN, {
  async verifyTest(ctx: restate.ObjectContext): Promise<boolean> {
    const isCanceled = (await ctx.get<boolean>("canceled")) ?? false;
    return isCanceled;
  },

  async startTest(ctx: restate.ObjectContext, request: BlockingOperation) {
    try {
      await ctx.object(api, ctx.key()).block(request);
    } catch (e) {
      if (
        e instanceof restate.TerminalError &&
        (e as restate.TerminalError).code === restate.ErrorCodes.CANCELLED
      ) {
        ctx.set("canceled", true);
      } else {
        throw e;
      }
    }
  },
});

const blockingService = restate.object(BlockingServiceFQN, {
  async block(ctx: restate.ObjectContext, request: BlockingOperation) {
    const { id, promise } = ctx.awakeable();
    // DO NOT await the next CALL otherwise the test deadlocks.
    ctx.object(AwakeableHolder, "cancel").hold(id);
    await promise;

    switch (request) {
      case BlockingOperation.CALL: {
        await ctx.object(api, ctx.key()).block(request);
        break;
      }
      case BlockingOperation.SLEEP: {
        await ctx.sleep(1_000_000_000);
        break;
      }
      case BlockingOperation.AWAKEABLE: {
        const { promise } = ctx.awakeable();
        // uncompletable promise >
        await promise;
        break;
      }
    }
  },

  async isUnlocked() {},
});

const api: typeof blockingService = { path : "CancelTestBlockingService" };
