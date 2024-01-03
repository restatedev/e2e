// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import {
  ClearRequest,
  ClearResponse,
  CommandVerifier,
  DeepPartial,
  ExecuteRequest,
  InspectRequest,
  InspectResponse,
  protobufPackage,
  VerificationRequest,
  VerificationResponse,
} from "./generated/verifier";
import {
  CallRequest,
  ClearRequest as InterpreterClearRequest,
  Command,
  CommandInterpreterClientImpl,
  Commands,
  VerificationRequest as InterpreterVerificationRequest,
} from "./generated/interpreter";
import seedrandom from "seedrandom";
import { useContext, TerminalError } from "@restatedev/restate-sdk";
import { Empty } from "./generated/google/protobuf/empty";
import { writeInterpreterKey } from "./interpreter";

const DEFAULT_MAX_SLEEP = 32768;

// "{width}-{depth}-{max_sleep_millis}-{seed}"
const testParamsRegex = /^(\d*)-(\d*)-(\d*)-(.*)$/m;

export function parseTestParams(key: string): {
  width: number;
  depth: number;
  maxSleepMillis: number;
  seed: string;
} {
  const match = testParamsRegex.exec(key);
  if (match) {
    return {
      width: parseInt(match[1]),
      depth: parseInt(match[2]),
      maxSleepMillis: parseInt(match[3]),
      seed: match[4],
    };
  }
  throw new Error("Unexpected test params");
}

export class CommandBuilder {
  random: () => number; // return a random float
  width: number;

  constructor(random: () => number, width: number) {
    this.random = random;
    this.width = width || 1;
  }

  randomInt(max: number) {
    return Math.floor(Math.abs(this.random() * max));
  }

  normaliseSleeps(commands: Commands | undefined, factor: number) {
    if (commands == undefined) {
      return;
    }
    for (let i = 0; i < commands.command.length; i++) {
      if (commands.command[i].sleep !== undefined) {
        const millis = commands.command[i].sleep?.milliseconds || 0;
        commands.command[i].sleep = {
          milliseconds: Math.floor(millis * factor),
        };
        continue;
      }

      this.normaliseSleeps(commands.command[i].asyncCall?.commands, factor);
      this.normaliseSleeps(commands.command[i].syncCall?.commands, factor);
      this.normaliseSleeps(
        commands.command[i].backgroundCall?.commands,
        factor
      );
    }
  }

  // durationUpperBound determines the upper bound on the runtime of a command set, by assuming that all sleeps
  // occur in sequence
  durationUpperBound(commands: Commands | undefined): number {
    if (commands == undefined) {
      return 0;
    }

    let duration = 0;
    for (const c of commands.command) {
      if (c.increment != undefined) {
        // increment has no effect on completion time
      } else if (c.asyncCall !== undefined) {
        duration += this.durationUpperBound(c.asyncCall.commands);
      } else if (c.asyncCallAwait !== undefined) {
        // already accounted for in c.asyncCall
      } else if (c.syncCall !== undefined) {
        duration += this.durationUpperBound(c.syncCall.commands);
      } else if (c.backgroundCall !== undefined) {
        duration += this.durationUpperBound(c.backgroundCall.commands);
      } else if (c.sleep !== undefined) {
        duration += c.sleep.milliseconds;
      }
    }
    return duration;
  }

  buildCommands(
    maxSleepMillis: number,
    depth: number
  ): { target: number; commands: Commands } {
    const call = this._buildCommands(0, depth, []);
    const duration = this.durationUpperBound(call.commands);
    // normalise so that the entire job takes less time than the max sleep
    this.normaliseSleeps(call.commands, maxSleepMillis / duration);
    return call;
  }

  _buildCommands(
    target: number,
    depth: number,
    lockedTargets: Array<number>
  ): { target: number; commands: Commands } {
    const commands = new Array<DeepPartial<Command>>();
    lockedTargets.push(target);

    if (depth === 0) {
      // last layer; all we can really do at this point is increment
      commands.push({ increment: {} });
      return { target, commands: Commands.create({ command: commands }) };
    }

    // ensure at least one command
    const numCommands = this.randomInt(this.width - 1) + 1;

    let asyncUnlockedCounter = 0; // keeps track of async calls to known-unlocked targets, which we may await
    let asyncLockedCounter = numCommands; // keeps track of async calls to known-locked targets, which we must not await

    const candidates: () => Array<() => DeepPartial<Command>> = () => [
      () => ({
        increment: {},
      }),
      () => ({
        // hit a known-unlocked target with a sync call, and pass on the lock list for future blocking calls
        syncCall: this._buildCommands(
          // jump to a target between 1 and 32 ahead
          // by only going upwards we avoid cycles
          // by skipping up to 32 we avoid all paths landing on the same few keys
          target + 1 + this.randomInt(32),
          depth - 1,
          [target, ...lockedTargets]
        ),
      }),
      () => ({
        asyncCall: {
          callId: asyncUnlockedCounter++,
          // hit a known-unlocked target with an async call that may be awaited, and pass on the lock list for future blocking calls
          ...this._buildCommands(target + 1 + this.randomInt(32), depth - 1, [
            target,
            ...lockedTargets,
          ]),
        },
      }),
      () => ({
        asyncCall: {
          callId: asyncLockedCounter++,
          // deliberately hit a known-locked target with an async call that must not be awaited
          ...this._buildCommands(
            [target, ...lockedTargets][
              this.randomInt(lockedTargets.length + 1)
            ],
            depth - 1,
            []
          ),
        },
      }),
      ...(asyncUnlockedCounter > 0
        ? [
            () => ({
              // await a previously made async call that was against a known-unlocked target
              // it's totally valid to await previous async calls multiple times, so we don't have to exclude any
              asyncCallAwait: { callId: this.randomInt(asyncUnlockedCounter) },
            }),
          ]
        : []),
      () => ({
        // deliberately hit a known-locked target with a background call (the call should just schedule after the target is unlocked)
        backgroundCall: this._buildCommands(
          [target, ...lockedTargets][this.randomInt(lockedTargets.length + 1)],
          depth - 1,
          []
        ),
      }),
      () => ({
        // deliberately hit a known-unlocked target with a background call (the call should schedule asap)
        backgroundCall: this._buildCommands(
          target + 1 + this.randomInt(32),
          depth - 1,
          []
        ),
      }),
      () => ({
        // this will be normalised later
        sleep: { milliseconds: this.random() },
      }),
    ];

    for (let i = 0; i < numCommands; i++) {
      const c = candidates();
      commands.push(c[this.randomInt(c.length)]());
    }

    return { target, commands: Commands.create({ command: commands }) };
  }
}

export const CommandVerifierServiceFQN = protobufPackage + ".CommandVerifier";

export class CommandVerifierService implements CommandVerifier {
  simulateCommands(
    m: Map<number, number>,
    target: number,
    commands: Commands | undefined
  ): void {
    if (!commands?.command) {
      throw new TerminalError("CallRequest with no commands");
    }
    for (const c of commands.command) {
      if (c.increment !== undefined) {
        m.set(target, (m.get(target) || 0) + 1);
      } else if (c.syncCall !== undefined) {
        this.simulateCommands(m, c.syncCall.target, c.syncCall.commands);
      } else if (c.asyncCall !== undefined) {
        this.simulateCommands(m, c.asyncCall.target, c.asyncCall.commands);
      } else if (c.asyncCallAwait !== undefined) {
        // do nothing
      } else if (c.backgroundCall !== undefined) {
        this.simulateCommands(
          m,
          c.backgroundCall.target,
          c.backgroundCall.commands
        );
      } else if (c.sleep !== undefined) {
        // do nothing
      } else {
        // should be unreachable
        throw new TerminalError("Empty Command in CallRequest");
      }
    }
  }

  async execute(request: ExecuteRequest): Promise<Empty> {
    if (!request.params) {
      throw new TerminalError("No params in ExecuteRequest");
    }
    const ctx = useContext(this);
    const params = parseTestParams(request.params);

    // we've already been called with these parameters; don't kick off the job a second time
    if (await ctx.get("started")) {
      return Empty.create({});
    } else {
      await ctx.set("started", true);
    }

    const client = new CommandInterpreterClientImpl(ctx);
    const builder = new CommandBuilder(seedrandom(params.seed), params.width);
    const { target, commands } = builder.buildCommands(
      params.maxSleepMillis || DEFAULT_MAX_SLEEP,
      params.depth
    );

    await client.call(
      CallRequest.create({
        key: writeInterpreterKey({ ...params, target }),
        commands,
      })
    );

    return Empty.create({});
  }

  async verify(request: VerificationRequest): Promise<VerificationResponse> {
    if (!request.params) {
      throw new TerminalError("No params in VerificationRequest");
    }
    const ctx = useContext(this);
    const params = parseTestParams(request.params);

    const client = new CommandInterpreterClientImpl(ctx);
    const builder = new CommandBuilder(seedrandom(params.seed), params.width);
    const { target, commands } = builder.buildCommands(
      params.maxSleepMillis || DEFAULT_MAX_SLEEP,
      params.depth
    );
    const m = new Map<number, number>();
    this.simulateCommands(m, target, commands);

    // fire off all the verification requests and see if any come back wrong
    await Promise.all(
      Array.from(m).map(async ([key, value]): Promise<void> => {
        const resp = await client.verify(
          InterpreterVerificationRequest.create({
            key: writeInterpreterKey({ ...params, target: key }),
            expected: value,
          })
        );
        if (resp.expected != value) {
          throw new TerminalError(
            `Incorrect value back for expected: sent ${value}, received ${resp.expected}`
          );
        }
        if (resp.expected != resp.actual) {
          throw new TerminalError(
            `Incorrect value for target ${key}: expected ${resp.expected}, got ${resp.actual}`
          );
        }
      })
    );

    return VerificationResponse.create({ counters: Object.fromEntries(m) });
  }

  async clear(request: ClearRequest): Promise<ClearResponse> {
    if (!request.params) {
      throw new TerminalError("No params in ClearRequest");
    }
    const ctx = useContext(this);
    const params = parseTestParams(request.params);

    // clear the idempotent flag, given that we can now execute again
    if (await ctx.get("started")) {
      ctx.clear("started");
    }
    const client = new CommandInterpreterClientImpl(ctx);
    const builder = new CommandBuilder(seedrandom(params.seed), params.width);
    const { target, commands } = builder.buildCommands(
      params.maxSleepMillis || DEFAULT_MAX_SLEEP,
      params.depth
    );
    const m = new Map<number, number>();
    this.simulateCommands(m, target, commands);

    await Promise.all(
      Array.from(m.keys()).map(async (key): Promise<void> => {
        await client.clear(
          InterpreterClearRequest.create({
            key: writeInterpreterKey({ ...params, target: key }),
          })
        );
      })
    );

    return ClearResponse.create({ targets: Array.from(m.keys()) });
  }

  async inspect(request: InspectRequest): Promise<InspectResponse> {
    if (!request.params) {
      throw new TerminalError("No params in InspectRequest");
    }
    const params = parseTestParams(request.params);

    const builder = new CommandBuilder(seedrandom(params.seed), params.width);
    const { target, commands } = builder.buildCommands(
      params.maxSleepMillis || DEFAULT_MAX_SLEEP,
      params.depth
    );
    return InspectResponse.create({
      call: { key: writeInterpreterKey({ ...params, target }), commands },
    });
  }
}
