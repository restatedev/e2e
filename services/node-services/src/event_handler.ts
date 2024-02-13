// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

import { Empty } from "./generated/google/protobuf/empty";
import { EventHandler, protobufPackage } from "./generated/event_handler";
import { Event } from "./generated/dev/restate/events";
import { CounterClientImpl } from "./generated/counter";

export const EventHandlerFQN = protobufPackage + ".EventHandler";

export class EventHandlerService implements EventHandler {
  async handle(event: Event): Promise<Empty> {
    console.log("handleEvent: " + JSON.stringify(event));
    const ctx = restate.useContext(this);

    const counterClient = new CounterClientImpl(ctx.grpcChannel());
    await ctx.grpcChannel().oneWayCall(() =>
      counterClient.add({
        counterName: event.key.toString(),
        value: parseInt(event.payload.toString()),
      })
    );

    return Empty.create({});
  }
}
