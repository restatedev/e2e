// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

import {
  CancelTestService as ICancelTestService,
  BlockingService as IBlockingService,
  protobufPackage,
  BlockingServiceClientImpl,
  Response,
  BlockingOperation,
  BlockingRequest,
} from "./generated/cancel_test";
import { Empty } from "./generated/google/protobuf/empty";
import { AwakeableHolderServiceClientImpl } from "./generated/awakeable_holder";

export const CancelTestServiceFQN = protobufPackage + ".CancelTestService";
export const BlockingServiceFQN = protobufPackage + ".BlockingService";

export class CancelTestService implements ICancelTestService {
  async verifyTest(request: Empty): Promise<Response> {
    const ctx = restate.useKeyedContext(this);
    const isCanceled = (await ctx.get<boolean>("canceled")) ?? false;

    return { isCanceled: isCanceled };
  }

  async startTest(request: BlockingRequest): Promise<Empty> {
    const ctx = restate.useKeyedContext(this);
    const client = new BlockingServiceClientImpl(ctx.grpcChannel());

    try {
      await client.block(request);
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

    return {};
  }
}

export class BlockingService implements IBlockingService {
  async block(request: BlockingRequest): Promise<Empty> {
    const ctx = restate.useKeyedContext(this);
    const client = new AwakeableHolderServiceClientImpl(ctx.grpcChannel());
    const self = new BlockingServiceClientImpl(ctx.grpcChannel());

    const { id, promise } = ctx.awakeable();
    client.hold({ name: "cancel", id });
    await promise;

    switch (request.operation) {
      case BlockingOperation.CALL: {
        await self.block(request);
        break;
      }
      case BlockingOperation.SLEEP: {
        await ctx.sleep(1_000_000_000);
        break;
      }
      case BlockingOperation.AWAKEABLE: {
        const { id: _, promise: uncompletable_promise } = ctx.awakeable();
        await uncompletable_promise;
        break;
      }
    }

    return {};
  }

  async isUnlocked(request: Empty): Promise<Empty> {
    return {};
  }
}
