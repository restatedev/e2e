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
  ComplexRequest,
  ComplexResponse,
  Coordinator,
  Duration,
  ManyTimersRequest,
  ProxyResponse,
  TimeoutResponse,
  protobufPackage,
} from "./generated/coordinator";
import { ReceiverClientImpl } from "./generated/receiver";
import { Empty } from "./generated/google/protobuf/empty";
import { v4 as uuidv4 } from "uuid";
import { TimeoutError } from "@restatedev/restate-sdk/dist/types/errors";

export const CoordinatorServiceFQN = protobufPackage + ".Coordinator";

export class CoordinatorService implements Coordinator {
  async sleep(request: Duration): Promise<Empty> {
    return this.manyTimers({ timer: [request] });
  }

  async manyTimers(request: ManyTimersRequest): Promise<Empty> {
    console.log("many timers: " + JSON.stringify(request));

    const ctx = restate.useContext(this);

    await restate.CombineablePromise.all(
      request.timer.map((value) => ctx.sleep(value.millis))
    );

    return {};
  }

  async proxy(): Promise<ProxyResponse> {
    console.log("proxy");
    const ctx = restate.useContext(this);
    const receiverClient = new ReceiverClientImpl(ctx.grpcChannel());

    const uuid = await ctx.sideEffect(async () => uuidv4());

    const pong = await receiverClient.ping({ key: uuid });

    return { message: pong.message };
  }

  async complex(request: ComplexRequest): Promise<ComplexResponse> {
    console.log("complex: ", request);
    const ctx = restate.useContext(this);
    const receiverClient = new ReceiverClientImpl(ctx.grpcChannel());

    const sleepDuration = request.sleepDuration?.millis;
    if (sleepDuration == undefined) {
      throw new Error("Expecting sleepDuration to be non null");
    }
    await ctx.sleep(sleepDuration);

    const key = await ctx.sideEffect(async () => uuidv4());

    // Functions should be invoked in the same order they were called. This means that
    // background calls as well as request-response calls have an absolute ordering that is defined
    // by their call order. In this concrete case, setValue is guaranteed to be executed before
    // getValue.
    await ctx
      .grpcChannel()
      .oneWayCall(() =>
        receiverClient.setValue({ key, value: request.requestValue })
      );
    const response = await receiverClient.getValue({ key });

    return { responseValue: response.value };
  }

  async timeout(request: Duration): Promise<TimeoutResponse> {
    const ctx = restate.useContext(this);
    let timeoutOccurred = false;

    try {
      await ctx.awakeable<string>().promise.orTimeout(request.millis);
    } catch (e) {
      if (e instanceof TimeoutError) {
        timeoutOccurred = true;
      }
    }

    return { timeoutOccurred };
  }

  invokeSequentially(): Promise<Empty> {
    throw new Error("Method not implemented.");
  }
}
