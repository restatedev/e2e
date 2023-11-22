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
  ErrorMessage,
  FailingService as IFailingService,
  FailingServiceClientImpl,
  protobufPackage,
  AttemptResponse,
  Request,
} from "./generated/errors";
import { Empty } from "./generated/google/protobuf/empty";

export const FailingServiceFQN = protobufPackage + ".FailingService";

export class FailingService implements IFailingService {
  // Need to be class fields because every invocation copies the service instance
  static eventualSuccessCalls = 0;
  static eventualSuccessSideEffectsCalls = 0;

  private readonly SUCCESS_ATTEMPT = 4;

  terminallyFailingCall(request: ErrorMessage): Promise<Empty> {
    console.log("fail: " + JSON.stringify(request));
    throw new restate.TerminalError(request.errorMessage);
  }

  async callTerminallyFailingCall(
    request: ErrorMessage
  ): Promise<ErrorMessage> {
    const ctx = restate.useContext(this);

    const failingService = new FailingServiceClientImpl(ctx);

    await failingService.terminallyFailingCall(
      ErrorMessage.create({
        errorMessage: request.errorMessage,
        key: request.key + "0",
      })
    );

    throw new Error("This should be unreachable");
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async failingCallWithEventualSuccess(
    request: Request
  ): Promise<AttemptResponse> {
    const currentAttempt = ++FailingService.eventualSuccessCalls;

    if (currentAttempt >= this.SUCCESS_ATTEMPT) {
      FailingService.eventualSuccessCalls = 0;
      return AttemptResponse.create({
        attempts: currentAttempt,
      });
    } else {
      throw new Error("Failed at attempt " + currentAttempt);
    }
  }

  async failingSideEffectWithEventualSuccess(
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    request: Request
  ): Promise<AttemptResponse> {
    const ctx = restate.useContext(this);

    const successAttempt = await ctx.sideEffect(async () => {
      const currentAttempt = ++FailingService.eventualSuccessSideEffectsCalls;

      if (currentAttempt >= this.SUCCESS_ATTEMPT) {
        FailingService.eventualSuccessSideEffectsCalls = 0;
        return currentAttempt;
      } else {
        throw new Error("Failed at attempt " + currentAttempt);
      }
    });

    return AttemptResponse.create({
      attempts: successAttempt,
    });
  }

  async terminallyFailingSideEffect(request: ErrorMessage): Promise<Empty> {
    const ctx = restate.useContext(this);

    await ctx.sideEffect(() => {
      throw new restate.TerminalError(request.errorMessage);
    });

    throw new Error("Should not be reachable");
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async failingSideEffectWithFiniteRetryPolicy(
    request: ErrorMessage
  ): Promise<Empty> {
    const ctx = restate.useContext(this);
    const failingAction = async () => {
      throw new Error("failing side effect action");
    };
    const retryPolicy = {
      maxRetries: 4,
    };

    await ctx.sideEffect(failingAction, retryPolicy);

    throw new Error("Should not be reachable");
  }

  invokeExternalAndHandleFailure(): Promise<ErrorMessage> {
    throw new restate.TerminalError("Method not implemented.");
  }
}
