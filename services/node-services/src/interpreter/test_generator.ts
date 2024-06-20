// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import { CommandType, Command, Program, AwaitPromise } from "./commands";
import { Random, WeightedRandom } from "./random";

/**
 * The following module is responsible for generating instances of a {@link Program}.
 * According to a predefined distributed.
 * There is nothing here that defines correctness, it is just about
 */

/**
 * L0 distribution represents the commands with their 'rank' (~ likelihood to appear)
 * The exact statistical distribution is defined in the distribution function below.
 */
const L0 = distribution([
  [
    CommandType.GET_STATE,
    CommandType.SET_STATE,
    CommandType.CLEAR_STATE,
    CommandType.SIDE_EFFECT,
    CommandType.INCREMENT_STATE_COUNTER,
  ],
  [
    CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY,
    CommandType.CALL_SERVICE,
    CommandType.CALL_NEXT_LAYER_OBJECT,
  ],
  [
    CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE,
    CommandType.RESOLVE_AWAKEABLE,
    CommandType.REJECT_AWAKEABLE,
  ],
  [CommandType.THROWING_SIDE_EFFECT, CommandType.RECOVER_TERMINAL_CALL],
  [
    CommandType.CALL_SLOW_SERVICE,
    CommandType.SLOW_SIDE_EFFECT,
    CommandType.SLEEP,
    CommandType.INCREMENT_VIA_DELAYED_CALL,
    CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED,
  ],
]);

/**
 * L1 distribution represents the commands with their 'rank' (~ likelihood to appear)
 */
const L1 = distribution([
  [
    CommandType.GET_STATE,
    CommandType.SET_STATE,
    CommandType.SIDE_EFFECT,
    CommandType.INCREMENT_STATE_COUNTER,
  ],
  [CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY, CommandType.CALL_SERVICE],
  [
    CommandType.CALL_NEXT_LAYER_OBJECT,
    CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE,
    CommandType.RESOLVE_AWAKEABLE,
    CommandType.REJECT_AWAKEABLE,
  ],
  [CommandType.THROWING_SIDE_EFFECT, CommandType.RECOVER_TERMINAL_CALL],
  [
    CommandType.CALL_SLOW_SERVICE,
    CommandType.SLOW_SIDE_EFFECT,
    CommandType.SLEEP,
    CommandType.INCREMENT_VIA_DELAYED_CALL,
    CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED,
  ],
]);

/**
 * L2 distribution represents the commands with their 'rank' (~ likelihood to appear)
 */
const L2 = distribution([
  [
    CommandType.GET_STATE,
    CommandType.SET_STATE,
    CommandType.CLEAR_STATE,
    CommandType.SIDE_EFFECT,
    CommandType.INCREMENT_STATE_COUNTER,
  ],
  [CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY, CommandType.CALL_SERVICE],
  [CommandType.THROWING_SIDE_EFFECT, CommandType.RECOVER_TERMINAL_CALL],
  [CommandType.SLEEP, CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE],
  [
    CommandType.CALL_SLOW_SERVICE,
    CommandType.SLOW_SIDE_EFFECT,
    CommandType.INCREMENT_VIA_DELAYED_CALL,
    CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED,
  ],
]);

const MAX_NUMBER_OF_LEVELS = 3;
const DISTRIBUTION_BY_LEVEL = [L0, L1, L2];

export class ProgramGenerator {
  constructor(
    readonly rand: Random,
    readonly interpreterCount: number,
    readonly maximumCommandCount: number
  ) {}

  random(low: number, high: number): number {
    return Math.floor(this.rand.random() * (high - low));
  }

  generateCommand(
    commandType: CommandType,
    currentLevel: number
  ): Command | null {
    if (commandType == CommandType.SET_STATE) {
      return {
        kind: CommandType.SET_STATE,
        key: this.random(0, 6),
      };
    } else if (commandType == CommandType.GET_STATE) {
      return {
        kind: CommandType.GET_STATE,
        key: this.random(0, 6),
      };
    } else if (commandType == CommandType.CLEAR_STATE) {
      return {
        kind: CommandType.CLEAR_STATE,
        key: this.random(0, 6),
      };
    } else if (commandType == CommandType.INCREMENT_STATE_COUNTER) {
      return { kind: CommandType.INCREMENT_STATE_COUNTER };
    } else if (commandType == CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY) {
      return { kind: CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY };
    } else if (commandType == CommandType.SLEEP) {
      return {
        kind: CommandType.SLEEP,
        duration: this.random(1, 101),
      };
    } else if (commandType == CommandType.CALL_SERVICE) {
      return { kind: CommandType.CALL_SERVICE };
    } else if (commandType == CommandType.CALL_SLOW_SERVICE) {
      return {
        kind: CommandType.CALL_SLOW_SERVICE,
        sleep: this.random(1, 101),
      };
    } else if (commandType == CommandType.INCREMENT_VIA_DELAYED_CALL) {
      return {
        kind: CommandType.INCREMENT_VIA_DELAYED_CALL,
        duration: this.random(1, 101),
      };
    } else if (commandType == CommandType.SIDE_EFFECT) {
      return { kind: CommandType.SIDE_EFFECT };
    } else if (commandType == CommandType.THROWING_SIDE_EFFECT) {
      return { kind: CommandType.THROWING_SIDE_EFFECT };
    } else if (commandType == CommandType.SLOW_SIDE_EFFECT) {
      return { kind: CommandType.SLOW_SIDE_EFFECT };
    } else if (commandType == CommandType.RECOVER_TERMINAL_CALL) {
      return { kind: CommandType.RECOVER_TERMINAL_CALL };
    } else if (commandType == CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED) {
      return { kind: CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED };
    } else if (commandType == CommandType.RESOLVE_AWAKEABLE) {
      return { kind: CommandType.RESOLVE_AWAKEABLE };
    } else if (commandType == CommandType.REJECT_AWAKEABLE) {
      return { kind: CommandType.REJECT_AWAKEABLE };
    } else if (
      commandType == CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE
    ) {
      return { kind: CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE };
    } else if (
      commandType == CommandType.CALL_NEXT_LAYER_OBJECT &&
      currentLevel < MAX_NUMBER_OF_LEVELS
    ) {
      const key = this.random(0, this.interpreterCount);
      return {
        kind: CommandType.CALL_NEXT_LAYER_OBJECT,
        key,
        program: this.generateProgram(currentLevel + 1),
      };
    }

    return null;
  }

  generateProgram(currentLevel: number): Program {
    const numCommands: number = this.random(0, this.maximumCommandCount);
    const commands: Command[] = [];
    const promises: number[] = [];
    const rand = this.rand;
    for (let i = 0; i < numCommands; i++) {
      const commandType = DISTRIBUTION_BY_LEVEL[currentLevel].next(rand);
      const command = this.generateCommand(commandType, currentLevel);
      if (command === null) {
        continue;
      }
      commands.push(command);
      if (canBeAwaited(commandType)) {
        promises.push(commands.length - 1);
      }
      if (promises.length > 0 && this.random(0, 100) < 80) {
        const index = promises[0];
        promises.shift();
        const awaitCommand: AwaitPromise = {
          kind: CommandType.AWAIT_PROMISE,
          index,
        };
        commands.push(awaitCommand);
      }
    }
    // last chance to await un-awaited commands
    for (let i = 0; i < promises.length; i++) {
      if (promises.length > 0 && this.random(0, 100) < 20) {
        // 20% skip
        continue;
      }
      const index = promises[i];
      const awaitCommand: AwaitPromise = {
        kind: CommandType.AWAIT_PROMISE,
        index,
      };
      commands.push(awaitCommand);
    }
    return { commands: commands };
  }
}

function distribution(ranks: CommandType[][]): WeightedRandom<CommandType> {
  const commands: Record<CommandType, number> = {
    [CommandType.SET_STATE]: 0,
    [CommandType.GET_STATE]: 0,
    [CommandType.CLEAR_STATE]: 0,
    [CommandType.INCREMENT_STATE_COUNTER]: 0,
    [CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY]: 0,
    [CommandType.SLEEP]: 0,
    [CommandType.CALL_SERVICE]: 0,
    [CommandType.CALL_SLOW_SERVICE]: 0,
    [CommandType.INCREMENT_VIA_DELAYED_CALL]: 0,
    [CommandType.SIDE_EFFECT]: 0,
    [CommandType.THROWING_SIDE_EFFECT]: 0,
    [CommandType.SLOW_SIDE_EFFECT]: 0,
    [CommandType.RECOVER_TERMINAL_CALL]: 0,
    [CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED]: 0,
    [CommandType.AWAIT_PROMISE]: 0,
    [CommandType.RESOLVE_AWAKEABLE]: 0,
    [CommandType.REJECT_AWAKEABLE]: 0,
    [CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE]: 0,
    [CommandType.CALL_NEXT_LAYER_OBJECT]: 0,
  };
  //
  // each group would have 2x smaller rank from the previous
  //
  let rank = 1 << (ranks.length + 1);
  for (const group of ranks) {
    for (const command of group) {
      commands[command] = rank;
    }
    rank = rank >> 2;
  }
  return WeightedRandom.from(commands);
}

function canBeAwaited(commandType: CommandType): boolean {
  return (
    commandType == CommandType.CALL_SERVICE ||
    commandType == CommandType.CALL_SLOW_SERVICE ||
    commandType == CommandType.RECOVER_TERMINAL_MAYBE_UN_AWAITED ||
    commandType == CommandType.CALL_NEXT_LAYER_OBJECT
  );
}
