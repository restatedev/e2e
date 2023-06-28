import * as restate from "@restatedev/restate-sdk";

import {
  ErrorMessage,
  FailingService as IFailingService,
  FailingServiceClientImpl,
  protobufPackage,
} from "./generated/errors";
import { Empty } from "./generated/google/protobuf/empty";

export const FailingServiceFQN = protobufPackage + ".FailingService";

export class FailingService implements IFailingService {
  fail(request: ErrorMessage): Promise<Empty> {
    console.log("fail: " + JSON.stringify(request));
    throw new Error(request.errorMessage);
  }

  async failAndHandle(request: ErrorMessage): Promise<ErrorMessage> {
    const ctx = restate.useContext(this);

    const failingService = new FailingServiceClientImpl(ctx);

    try {
      await failingService.fail(
        ErrorMessage.create({
          errorMessage: request.errorMessage,
          key: request.key + "0",
        })
      );
    } catch (error) {
      if (error instanceof Error) {
        return ErrorMessage.create({ errorMessage: error.message });
      }
      throw new Error("Error is not instanceof Error: " + typeof error);
    }

    throw new Error("This should be unreachable");
  }

  invokeExternalAndHandleFailure(): Promise<ErrorMessage> {
    throw new Error("Method not implemented.");
  }
}
