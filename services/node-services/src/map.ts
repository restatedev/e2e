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
  ClearAllRequest,
  ClearAllResponse,
  GetRequest,
  GetResponse,
  MapService as IMapService,
  protobufPackage,
  SetRequest,
} from "./generated/map";
import { Empty } from "./generated/google/protobuf/empty";

export const MapServiceFQN = protobufPackage + ".MapService";

export class MapService implements IMapService {
  async clearAll(): Promise<ClearAllResponse> {
    const ctx = restate.useContext(this);

    const keys = await ctx.stateKeys();
    ctx.clearAll();

    return { keys };
  }

  async get(request: GetRequest): Promise<GetResponse> {
    const ctx = restate.useContext(this);

    const value = (await ctx.get<string>(request.key)) || "";

    return { value };
  }

  async set(request: SetRequest): Promise<Empty> {
    const ctx = restate.useContext(this);

    ctx.set(request.key, request.value);

    return {};
  }
}
