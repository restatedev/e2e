import * as restate from "@restatedev/restate-sdk";

import {
  Counter,
  CounterRequest,
  CounterAddRequest,
  CounterUpdateResult,
  GetResponse,
  protobufPackage
} from "./generated/counter";
import {
  Empty
} from "./generated/google/protobuf/empty"

const COUNTER_KEY = "counter"

export const CounterServiceFQN = protobufPackage + ".Counter"

export class CounterService implements Counter {
  async reset(request: CounterRequest): Promise<Empty> {
    console.log("reset: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    ctx.clear(COUNTER_KEY);

    return Empty.create({});
  }
  async add(request: CounterAddRequest): Promise<Empty> {
    console.log("add: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    let value = (await ctx.get<number>(COUNTER_KEY)) || 0;
    ctx.set(COUNTER_KEY, value + request.value);

    return Empty.create({});
  }
  addThenFail(request: CounterAddRequest): Promise<Empty> {
    throw new Error("Method not implemented.");
  }
  async get(request: CounterRequest): Promise<GetResponse> {
    console.log("get: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    let value = (await ctx.get<number>(COUNTER_KEY)) || 0;

    return GetResponse.create({ value });
  }
  async getAndAdd(request: CounterAddRequest): Promise<CounterUpdateResult> {
    console.log("getAndAdd: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    let oldValue = (await ctx.get<number>(COUNTER_KEY)) || 0;
    let newValue = oldValue + request.value;
    ctx.set(COUNTER_KEY, newValue);

    return CounterUpdateResult.create({ oldValue, newValue });
  }
}
