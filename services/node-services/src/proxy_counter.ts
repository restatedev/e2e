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
