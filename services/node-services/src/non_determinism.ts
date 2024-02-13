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
    const counterClient = new CounterClientImpl(ctx.grpcChannel());

    if (doLeftAction(request)) {
      await ctx.sleep(100);
    } else {
      await counterClient.get(CounterRequest.create({ counterName: "abc" }));
    }

    return await this.incrementCounterAndEnd(request);
  }
  async callDifferentMethod(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useContext(this);
    const counterClient = new CounterClientImpl(ctx.grpcChannel());

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
    const counterClient = new CounterClientImpl(ctx.grpcChannel());

    if (doLeftAction(request)) {
      await ctx
        .grpcChannel()
        .oneWayCall(() =>
          counterClient.get(CounterRequest.create({ counterName: "abc" }))
        );
    } else {
      await ctx
        .grpcChannel()
        .oneWayCall(() =>
          counterClient.reset(CounterRequest.create({ counterName: "abc" }))
        );
    }

    await ctx.sleep(100);
    return await this.incrementCounterAndEnd(request);
  }
  async setDifferentKey(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useKeyedContext(this);

    if (doLeftAction(request)) {
      ctx.set("a", "my-state");
    } else {
      ctx.set("b", "my-state");
    }

    await ctx.sleep(100);
    return await this.incrementCounterAndEnd(request);
  }
  async setDifferentValue(request: NonDeterministicRequest): Promise<Empty> {
    const ctx = restate.useKeyedContext(this);
    ctx.set("a", uuidv4());
    await ctx.sleep(100);
    return await this.incrementCounterAndEnd(request);
  }

  async incrementCounterAndEnd(
    request: NonDeterministicRequest
  ): Promise<Empty> {
    const ctx = restate.useContext(this);
    const counterClient = new CounterClientImpl(ctx.grpcChannel());

    await ctx
      .grpcChannel()
      .oneWayCall(() =>
        counterClient.add(
          CounterAddRequest.create({ counterName: request.key, value: 1 })
        )
      );

    return Empty.create({});
  }
}
