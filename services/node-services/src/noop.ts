import * as restate from "@restatedev/restate-sdk";

import {
  Noop,
  protobufPackage
} from "./generated/noop";
import {
  CounterClientImpl
} from "./generated/counter";
import {
  Empty
} from "./generated/google/protobuf/empty"

export const NoopServiceFQN = protobufPackage + ".Noop"

export class NoopService implements Noop {
  async doAndReportInvocationCount(request: Empty): Promise<Empty> {
    console.log("doAndReportInvocationCount");
    const ctx = restate.useContext(this);

    const productServiceClient = new CounterClientImpl(ctx);
    await ctx.inBackground(() =>
      productServiceClient.add({
        counterName: "doAndReportInvocationCount",
        value: 1
      })
    );

    return {}
  }
}
