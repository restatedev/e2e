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
  KillTestService as IKillTestService,
  KillSingletonService as IKillSingletonService,
  protobufPackage,
  KillSingletonServiceClientImpl,
} from "./generated/kill_test";
import { Empty } from "./generated/google/protobuf/empty";
import {
  AwakeableHolderServiceClientImpl,
  HoldRequest,
} from "./generated/awakeable_holder";

export const KillTestServiceFQN = protobufPackage + ".KillTestService";
export const KillSingletonServiceFQN =
  protobufPackage + ".KillSingletonService";

export class KillTestService implements IKillTestService {
  // The call tree method invokes the KillSingletonService::recursiveCall which blocks on calling itself again.
  // This will ensure that we have a call tree that is two calls deep and has a pending invocation in the inbox:
  // startCallTree --> recursiveCall --> recursiveCall:inboxed
  async startCallTree(request: Empty): Promise<Empty> {
    const ctx = restate.useContext(this);
    const singletonClient = new KillSingletonServiceClientImpl(ctx);

    await singletonClient.recursiveCall(Empty.create({}));

    return Empty.create({});
  }
}

export class KillSingletonService implements IKillSingletonService {
  async recursiveCall(request: Empty): Promise<Empty> {
    const ctx = restate.useContext(this);
    const selfClient = new KillSingletonServiceClientImpl(ctx);
    const awakeableClient = new AwakeableHolderServiceClientImpl(ctx);

    const { id, promise } = ctx.awakeable();
    awakeableClient.hold({
      name: "kill",
      id,
    });
    await promise;

    await selfClient.recursiveCall(Empty.create({}));
    return Empty.create({});
  }

  async isUnlocked(request: Empty): Promise<Empty> {
    return Empty.create({});
  }
}
