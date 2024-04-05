// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import { Program, CommandType } from "./commands";
import {
  createInterpreterObject,
  interpreterObjectForLayer,
} from "./interpreter";
import { Random } from "./random";
import { ProgramGenerator } from "./test_generator";

import * as restate from "@restatedev/restate-sdk";

const MAX_LAYERS = 3;

export interface TestConfiguration {
  readonly ingress: string;
  readonly seed: string;
  readonly keys: number;
  readonly tests: number;
  readonly maxProgramSize: number;
}

export enum TestStatus {
  NOT_STARTED = "NOT_STARTED",
  RUNNING = "RUNNING",
  VALIDATING = "VALIDATING",
  FINISHED = "FINISHED",
  FAILED = "FAILED",
}

class StateTracker {
  private states: number[][] = [];

  constructor(
    private readonly numLayers: number,
    private readonly numInterpreters: number
  ) {
    for (let i = 0; i < numLayers; i++) {
      const layerState = Array.from({ length: this.numInterpreters }, () => 0);
      this.states.push(layerState);
    }
  }

  update(layer: number, id: number, program: Program) {
    if (layer >= this.numLayers) {
      throw new Error(`InterpreterDriver bug.`);
    }
    for (const command of program.commands) {
      switch (command.kind) {
        case CommandType.INCREMENT_STATE_COUNTER:
        case CommandType.INCREMENT_STATE_COUNTER_INDIRECTLY:
        case CommandType.INCREMENT_VIA_DELAYED_CALL:
        case CommandType.INCREMENT_STATE_COUNTER_VIA_AWAKEABLE: {
          this.states[layer][id] += 1;
          break;
        }
        case CommandType.CALL_NEXT_LAYER_OBJECT: {
          this.update(layer + 1, command.key, command.program);
          break;
        }
      }
    }
  }

  getLayer(layer: number): number[] {
    return this.states[layer];
  }
}

export class Test {
  readonly random: Random;
  readonly stateTracker: StateTracker;
  readonly ingress: restate.ingress.Ingress;

  status: TestStatus = TestStatus.NOT_STARTED;

  constructor(readonly conf: TestConfiguration) {
    this.random = new Random(conf.seed);
    this.stateTracker = new StateTracker(MAX_LAYERS, conf.keys);
    this.ingress = restate.ingress.connect({ url: this.conf.ingress });
  }

  testStatus(): TestStatus {
    return this.status;
  }

  *generate() {
    const testCaseCount = this.conf.tests;
    const gen = new ProgramGenerator(
      this.random,
      this.conf.keys,
      this.conf.maxProgramSize
    );

    for (let i = 0; i < testCaseCount; i++) {
      const program = gen.generateProgram(0);
      const id = Math.floor(this.random.random() * this.conf.keys);
      yield { id, program };
    }
  }

  async go(): Promise<TestStatus> {
    this.status = TestStatus.RUNNING;
    for (const b of batch(this.generate(), 16)) {
      // TODO: add `idempotencyKey` once supported.
      const promises = b.map(({ id, program }) =>
        this.ingress.objectSendClient(InterpreterL0, `${id}`).interpret(program)
      );

      b.forEach(({ id, program }) => this.stateTracker.update(0, id, program));

      await Promise.all(promises);
    }

    this.status = TestStatus.VALIDATING;
    console.log("Done generating");

    for (const layerId of [0, 1, 2]) {
      while (!(await this.verifyLayer(layerId))) {
        await new Promise((resolve) => setTimeout(resolve, 10 * 1000));
      }
      console.log(`Done validating layer ${layerId}`);
    }

    console.log("Done.");
    this.status = TestStatus.FINISHED;
    return TestStatus.FINISHED;
  }

  async verifyLayer(layerId: number): Promise<boolean> {
    console.log(`Trying to verify layer ${layerId}`);

    const layer = this.stateTracker.getLayer(layerId);
    const interpreterLn = createInterpreterObject(layerId);
    const futures = layer.map(async (expected, id) => {
      const actual = await this.ingress
        .objectClient(interpreterLn, `${id}`)
        .counter();
      return expected === actual;
    });

    const results = await Promise.all(futures);
    for (let j = 0; j < layer.length; j++) {
      const p = results[j];
      if (!p) {
        console.log(
          `Found a mismatch at layer ${layerId} interpreter ${j}. This is expected, this interpreter might still have some backlog to process, and eventually it will catchup with the desired value. Will retry in few seconds.`
        );
        return false;
      }
    }
    return true;
  }
}

function* batch<T>(iterable: IterableIterator<T>, batchSize: number) {
  let items: T[] = [];
  for (const item of iterable) {
    items.push(item);
    if (items.length >= batchSize) {
      yield items;
      items = [];
    }
  }
  if (items.length !== 0) {
    yield items;
  }
}

const InterpreterL0 = interpreterObjectForLayer(0);
