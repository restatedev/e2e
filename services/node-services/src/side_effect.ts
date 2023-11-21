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
  InvokeSideEffectsResult,
  SideEffect,
  protobufPackage,
} from "./generated/side_effect";

export const SideEffectServiceFQN = protobufPackage + ".SideEffect";

export class SideEffectService implements SideEffect {
  async invokeSideEffects(): Promise<InvokeSideEffectsResult> {
    console.log("invokeSideEffects");
    const ctx = restate.useContext(this);

    let invocationCount = 0;

    await ctx.sideEffect<void>(async () => {
      invocationCount++;
    });
    await ctx.sideEffect<void>(async () => {
      invocationCount++;
    });
    await ctx.sideEffect<void>(async () => {
      invocationCount++;
    });

    return { nonDeterministicInvocationCount: invocationCount };
  }
}
