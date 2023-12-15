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
  HasAwakeableRequest,
  HasAwakeableResponse,
  HoldRequest,
  AwakeableHolderService as IAwakeableHolderService,
  UnlockRequest,
  protobufPackage,
} from "./generated/awakeable_holder";
import { Empty } from "./generated/google/protobuf/empty";

const ID_KEY = "id";

export const AwakeableHolderServiceFQN =
  protobufPackage + ".AwakeableHolderService";

export class AwakeableHolderService implements IAwakeableHolderService {
  async hold(request: HoldRequest): Promise<Empty> {
    console.log("hold: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    ctx.set(ID_KEY, request.id);

    return {};
  }

  async hasAwakeable(
    request: HasAwakeableRequest
  ): Promise<HasAwakeableResponse> {
    console.log("hasAwakeable: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    return {
      hasAwakeable: (await ctx.get<string>(ID_KEY)) !== null,
    };
  }

  async unlock(request: UnlockRequest): Promise<Empty> {
    console.log("unlock: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    const id = await ctx.get<string>(ID_KEY);
    if (id === null || id === undefined) {
      throw new Error("No awakeable registered");
    }

    ctx.resolveAwakeable(id, request.payload);
    ctx.clear(ID_KEY);

    return {};
  }
}
