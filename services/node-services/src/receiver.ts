import * as restate from "@restatedev/restate-sdk";

import {
  GetValueRequest,
  GetValueResponse,
  PingRequest,
  Pong,
  Receiver,
  SetValueRequest,
  protobufPackage,
} from "./generated/receiver";
import { Empty } from "./generated/google/protobuf/empty";

export const ReceiverServiceFQN = protobufPackage + ".Receiver";

const STATE_KEY = "my-state";

export class ReceiverService implements Receiver {
  async ping(request: PingRequest): Promise<Pong> {
    console.log(`ping: ${request}`);
    return { message: "pong" };
  }

  async setValue(request: SetValueRequest): Promise<Empty> {
    console.log("setValue: " + request);
    const ctx = restate.useContext(this);

    ctx.set(STATE_KEY, request.value);

    return {};
  }

  async getValue(request: GetValueRequest): Promise<GetValueResponse> {
    console.log(`getValue: ${request}`);
    const ctx = restate.useContext(this);

    const value = (await ctx.get<string>(STATE_KEY)) || "";

    return { value };
  }
}
