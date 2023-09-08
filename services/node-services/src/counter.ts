import * as restate from "@restatedev/restate-sdk";

import {
  Counter,
  CounterRequest,
  CounterAddRequest,
  CounterUpdateResult,
  GetResponse,
  protobufPackage,
} from "./generated/counter";
import {
  StringKeyedEvent
} from "./generated/dev/restate/events";
import { Empty } from "./generated/google/protobuf/empty";
import { AwakeableHolderServiceClientImpl } from "./generated/awakeable_holder";

const COUNTER_KEY = "counter";

export const CounterServiceFQN = protobufPackage + ".Counter";

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

    const value = (await ctx.get<number>(COUNTER_KEY)) || 0;
    ctx.set(COUNTER_KEY, value + request.value);

    return Empty.create({});
  }

  async addThenFail(request: CounterAddRequest): Promise<Empty> {
    await this.add(request);

    throw new restate.TerminalError(request.counterName);
  }

  async get(request: CounterRequest): Promise<GetResponse> {
    console.log("get: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    const value = (await ctx.get<number>(COUNTER_KEY)) || 0;

    return GetResponse.create({ value });
  }

  async getAndAdd(request: CounterAddRequest): Promise<CounterUpdateResult> {
    console.log("getAndAdd: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    const oldValue = (await ctx.get<number>(COUNTER_KEY)) || 0;
    const newValue = oldValue + request.value;
    ctx.set(COUNTER_KEY, newValue);

    return CounterUpdateResult.create({ oldValue, newValue });
  }

  async infiniteIncrementLoop(request: CounterRequest): Promise<Empty> {
    console.log("infiniteIncrementLoop: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    let counter = 1;
    ctx.set(COUNTER_KEY, counter);

    // Wait for the sync with the test runner
    const awakeableHolderClient = new AwakeableHolderServiceClientImpl(ctx);
    const { id, promise } = ctx.awakeable();
    awakeableHolderClient.hold({
      name: request.counterName,
      id,
    });
    await promise;

    // Now start looping
    // eslint-disable-next-line no-constant-condition
    while (true) {
      counter++;
      ctx.set(COUNTER_KEY, counter);
      await ctx.sleep(50); // Short sleeps to slow down the loop
    }
  }

    async handleEvent(request: StringKeyedEvent): Promise<Empty> {
      console.log("handleEvent: " + JSON.stringify(request));
      const ctx = restate.useContext(this);

      const value = (await ctx.get<number>(COUNTER_KEY)) || 0;
      ctx.set(COUNTER_KEY, value + parseInt(request.payload.toString());

      return Empty.create({});
    }
}
