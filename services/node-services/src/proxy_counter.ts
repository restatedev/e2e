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
  ProxyCounter as IProxyCounter,
  CounterAddRequest,
  CounterClientImpl,
  protobufPackage,
} from "./generated/counter";
import { Empty } from "./generated/google/protobuf/empty";

export const ProxyCounterServiceFQN = protobufPackage + ".ProxyCounter";

export class ProxyCounterService implements IProxyCounter {
  async addInBackground(request: CounterAddRequest): Promise<Empty> {
    console.log("addInBackground " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    const productServiceClient = new CounterClientImpl(ctx);
    await ctx.oneWayCall(() => productServiceClient.add(request));

    return {};
  }
}
