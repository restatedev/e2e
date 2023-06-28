import * as restate from "@restatedev/restate-sdk";
import { Replier, Reply } from "./generated/replier";
import { Empty } from "./generated/google/protobuf/empty";
import { protobufPackage } from "./generated/replier";

export const ReplierServiceFQN = protobufPackage + ".Replier";

export class ReplierService implements Replier {
  async replyToRandomNumberListGenerator(request: Reply): Promise<Empty> {
    const ctx = restate.useContext(this);

    ctx.completeAwakeable(request.replyIdentifier.toString(), request.payload)

    return Empty.create({});
  }
}