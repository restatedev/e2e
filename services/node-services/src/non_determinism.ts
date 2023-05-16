import * as restate from "@restatedev/restate-sdk";

import {
  NonDeterministicService as INonDeterministicService,
  NonDeterministicRequest,
  protobufPackage,
} from "./generated/non_determinism";
import {
  CounterAddRequest,
  CounterClientImpl,
  CounterRequest,
} from "./generated/counter";
import { Empty } from "./generated/google/protobuf/empty";
import { v4 as uuidv4 } from "uuid";

const invocationCounts = new Map<string, number>();

function doLeftAction(req: NonDeterministicRequest): boolean {
  const newValue = (invocationCounts.get(req.key) ?? 0) + 1;
  invocationCounts.set(req.key, newValue);
  return newValue % 2 == 1;
}

export const NonDeterministicServiceFQN =
  protobufPackage + ".NonDeterministicService";

export class NonDeterministicService implements INonDeterministicService {
  async leftSleepRightCall(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useContext(this);
    const counterClient = new CounterClientImpl(ctx);

    if (doLeftAction(request)) {
      await ctx.sleep(100);
    } else {
      await counterClient.get(CounterRequest.create({ counterName: "abc" }));
    }

    return await this.incrementCounterAndEnd(request);
  }
  async callDifferentMethod(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useContext(this);
    const counterClient = new CounterClientImpl(ctx);

    if (doLeftAction(request)) {
      await counterClient.get(CounterRequest.create({ counterName: "abc" }));
    } else {
      await counterClient.reset(CounterRequest.create({ counterName: "abc" }));
    }

    return await this.incrementCounterAndEnd(request);
  }
  async backgroundInvokeWithDifferentTargets(
    request: NonDeterministicRequest
  ): Promise<Empty> {
    const ctx = restate.useContext(this);
    const counterClient = new CounterClientImpl(ctx);

    if (doLeftAction(request)) {
      await ctx.inBackground(() =>
        counterClient.get(CounterRequest.create({ counterName: "abc" }))
      );
    } else {
      await ctx.inBackground(() =>
        counterClient.reset(CounterRequest.create({ counterName: "abc" }))
      );
    }

    await ctx.sleep(100);
    return await this.incrementCounterAndEnd(request);
  }
  async setDifferentKey(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useContext(this);

    if (doLeftAction(request)) {
      ctx.set("a", "my-state");
    } else {
      ctx.set("b", "my-state");
    }

    await ctx.sleep(100);
    return await this.incrementCounterAndEnd(request);
  }
  async setDifferentValue(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useContext(this);
    ctx.set("a", uuidv4());
    await ctx.sleep(100);
    return await this.incrementCounterAndEnd(request);
  }

  async incrementCounterAndEnd(
    request: NonDeterministicRequest
  ): Promise<Empty> {
    const ctx = restate.useContext(this);
    const counterClient = new CounterClientImpl(ctx);

    await ctx.inBackground(() =>
      counterClient.add(
        CounterAddRequest.create({ counterName: request.key, value: 1 })
      )
    );

    return Empty.create({});
  }
}
