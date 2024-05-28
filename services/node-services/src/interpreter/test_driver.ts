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

import * as restate from "@restatedev/restate-sdk-clients";

const MAX_LAYERS = 3;

export interface TestConfigurationDeployments {
  adminUrl: string;
  deployments: string[];
}

export interface TestConfiguration {
  readonly ingress: string;
  readonly seed: string;
  readonly keys: number;
  readonly tests: number;
  readonly maxProgramSize: number;
  readonly register?: TestConfigurationDeployments; // auto register the following endpoints
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
      const layerState = [];
      for (let j = 0; j < this.numInterpreters; j++) {
        layerState.push(0);
      }
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
  readonly ingress: restate.Ingress;

  status: TestStatus = TestStatus.NOT_STARTED;

  constructor(readonly conf: TestConfiguration) {
    this.random = new Random(conf.seed);
    this.stateTracker = new StateTracker(MAX_LAYERS, conf.keys);
    this.ingress = restate.connect({ url: this.conf.ingress });
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

  async ingressReady() {
    if (!this.conf.ingress) {
      throw new Error(`missing ingress url`);
    }
    const url = this.conf.ingress;
    for (;;) {
      try {
        const rc = await fetch(`${url}/restate/health`);
        if (rc.ok) {
          break;
        }
      } catch (e) {
        // suppress
      }
      console.log(`Waiting for ${url} to be healthy...`);
      await sleep(1000);
    }
    console.log(`Ingress is ready.`);
  }

  async registerEndpoints() {
    const adminUrl = this.conf.register?.adminUrl;
    if (!adminUrl) {
      throw new Error("Missing adminUrl");
    }
    for (;;) {
      try {
        const rc = await fetch(`${adminUrl}/health`);
        if (rc.ok) {
          break;
        }
      } catch (e) {
        // suppress
      }
      console.log(`Waiting for ${adminUrl} to be healthy...`);
      await sleep(1000);
    }
    for (;;) {
      try {
        const rc = await fetch(`${adminUrl}/health`);
        if (rc.ok) {
          break;
        }
      } catch (e) {
        // suppress
      }
      console.log(`Waiting for ${adminUrl} to be healthy...`);
      await sleep(1000);
    }
    const deployments = this.conf.register?.deployments;
    if (!deployments) {
      throw new Error("Missing register.deployments (array of uri string)");
    }
    for (const uri of deployments) {
      const res = await fetch(`${adminUrl}/deployments`, {
        method: "POST",
        body: JSON.stringify({
          uri,
        }),
        headers: {
          "Content-Type": "application/json",
        },
      });
      if (!res.ok) {
        throw new Error(
          `unable to register ${uri} because: ${await res.text()}`
        );
      }
    }
    console.log("Registered deployments");
  }

  async go(): Promise<TestStatus> {
    console.log(this.conf);
    this.status = TestStatus.RUNNING;
    if (this.conf.register) {
      await this.registerEndpoints();
    }
    await this.ingressReady();
    console.log("Generating ...");
    let idempotencyKey = 1;
    for (const b of batch(this.generate(), 32)) {
      const promises = b.map(({ id, program }) => {
        idempotencyKey += 1;
        const key = `${idempotencyKey}`;
        const client = this.ingress.objectSendClient(InterpreterL0, `${id}`);
        return retry(() =>
          client.interpret(
            program,
            restate.SendOpts.from({
              idempotencyKey: key,
            })
          )
        );
      });

      b.forEach(({ id, program }) => this.stateTracker.update(0, id, program));
      try {
        await Promise.all(promises);
      } catch (e) {
        console.error(e);
        throw e;
      }
    }

    this.status = TestStatus.VALIDATING;
    console.log("Done generating");

    for (const layerId of [0, 1, 2]) {
      while (!(await this.verifyLayer(layerId))) {
        await sleep(10 * 1000);
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

    for (const layerChunk of batch(iterate(layer), 256)) {
      const futures = layerChunk.map(async ({ expected, id }) => {
        const actual = await retry(
          async () =>
            await this.ingress.objectClient(interpreterLn, `${id}`).counter()
        );
        return { expected, actual, id };
      });
      await Promise.all(futures);
      for await (const { expected, actual, id } of futures) {
        if (expected !== actual) {
          console.log(
            `Found a mismatch at layer ${layerId} interpreter ${id}. Expected ${expected} but got ${actual}. This is expected, this interpreter might still have some backlog to process, and eventually it will catchup with the desired value. Will retry in few seconds.`
          );
          return false;
        }
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

function* iterate(arr: number[]) {
  for (let j = 0; j < arr.length; j++) {
    const expected = arr[j];
    const id = j;
    yield { id, expected };
  }
}

const sleep = (duration: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, duration));

const retry = async <T>(op: () => Promise<T>): Promise<T> => {
  let error;
  for (let i = 0; i < 60; i++) {
    try {
      return await op();
    } catch (e) {
      error = e;
      await sleep(1000);
    }
  }
  throw error;
};

const InterpreterL0 = interpreterObjectForLayer(0);
