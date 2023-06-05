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
