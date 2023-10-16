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

    const counterClient = new CounterClientImpl(ctx);
    await ctx.oneWayCall(() =>
      counterClient.add({
        counterName: event.key.toString(),
        value: parseInt(event.payload.toString()),
      })
    );

    return Empty.create({});
  }
}
