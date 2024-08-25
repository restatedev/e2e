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
import {
  EnvironmentSpec,
  setupContainers,
  tearDown,
  TestEnvironment,
} from "./test_containers";
import { ProgramGenerator } from "./test_generator";

import * as restate from "@restatedev/restate-sdk-clients";
import { batch, iterate, retry, sleep } from "./utils";

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
  readonly bootstrap?: boolean;
  readonly crashInterval?: number;
  readonly bootstrapEnv?: EnvironmentSpec;
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

  status: TestStatus = TestStatus.NOT_STARTED;
  containers: TestEnvironment | undefined = undefined;

  constructor(readonly conf: TestConfiguration) {
    this.random = new Random(conf.seed);
    this.stateTracker = new StateTracker(MAX_LAYERS, conf.keys);
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

    const keys = this.conf.keys;
    const rnd = this.random;
    for (let i = 0; i < testCaseCount; i++) {
      const program = gen.generateProgram(0);
      const id = Math.floor(rnd.random() * keys);
      yield { id, program };
    }
  }

  async ingressReady(ingressUrl: string) {
    for (;;) {
      try {
        const rc = await fetch(`${ingressUrl}/restate/health`);
        if (rc.ok) {
          break;
        }
      } catch (e) {
        // suppress
      }
      console.log(`Waiting for ${ingressUrl} to be healthy...`);
      await sleep(2000);
    }
    console.log(`Ingress is ready.`);
  }

  async registerEndpoints(
    useHttp11?: boolean,
    adminUrl?: string,
    deployments?: string[]
  ) {
    if (!adminUrl) {
      throw new Error("Missing adminUrl");
    }
    if (!deployments) {
      throw new Error("Missing register.deployments (array of uri string)");
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
      await sleep(2000);
    }
    console.log("Admin is ready.");

    for (const uri of deployments) {
      const res = await fetch(`${adminUrl}/deployments`, {
        method: "POST",
        body: JSON.stringify({
          uri,
          use_http_11: useHttp11 ?? false ? true : false,
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
    try {
      return await this.startTesting();
    } catch (e) {
      console.error(e);
      this.status = TestStatus.FAILED;
      throw e;
    } finally {
      await this.cleanup();
    }
  }

  async startTesting(): Promise<TestStatus> {
    console.log(this.conf);
    this.status = TestStatus.RUNNING;

    const useHttp11 = process.env.E2E_USE_FETCH?.toLocaleLowerCase() == "true";
    if (this.conf.bootstrap) {
      const env: EnvironmentSpec = this.conf.bootstrapEnv ?? {
        restate: {
          image: "ghcr.io/restatedev/restate:main",
          env: {},
        },

        interpreters: {
          image: "ghcr.io/restatedev/e2e-node-services:main",
          env: {},
        },

        service: {
          image: "ghcr.io/restatedev/e2e-node-services:main",
          env: {},
        },
      };
      this.containers = await setupContainers(env);
      console.log(this.containers);
    }
    const ingressUrl = this.containers?.ingressUrl ?? this.conf.ingress;
    const adminUrl = this.containers?.adminUrl ?? this.conf.register?.adminUrl;
    const deployments =
      this.containers?.services ?? this.conf.register?.deployments;

    let ingress = restate.connect({ url: ingressUrl });

    if (deployments) {
      console.log(useHttp11);
      console.log(adminUrl);
      console.log(deployments);
      await this.registerEndpoints(useHttp11, adminUrl, deployments);
    }
    await this.ingressReady(ingressUrl);

    console.log("Generating ...");

    const killRestate = async () => {
      const container = this.containers?.containers.restateContainer;
      if (!container) {
        return;
      }
      const interval = this.conf.crashInterval;
      if (!interval) {
        return;
      }
      for (;;) {
        await sleep(interval);
        if (
          this.status == TestStatus.FAILED ||
          this.status == TestStatus.FINISHED
        ) {
          break;
        }
        console.log("Killing restate");
        await container.restart({ timeout: 1 });
        const newIngressUrl = `http://${container.getHost()}:${container.getMappedPort(
          8080
        )}`;
        const newAdminUrl = `http://${container.getHost()}:${container.getMappedPort(
          9070
        )}`;
        ingress = restate.connect({ url: newIngressUrl });
        console.log(
          `Restate is back:\ningress: ${newIngressUrl}\nadmin:  ${newAdminUrl}`
        );
      }
    };

    killRestate().catch(console.error);

    let idempotencyKey = 1;
    for (const b of batch(this.generate(), 32)) {
      const promises = b.map(({ id, program }) => {
        idempotencyKey += 1;
        const key = `${idempotencyKey}`;
        return retry(() => {
          const client = ingress.objectSendClient(InterpreterL0, `${id}`);
          return client.interpret(
            program,
            restate.SendOpts.from({
              idempotencyKey: key,
            })
          );
        });
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
      try {
        while (!(await this.verifyLayer(ingress, layerId))) {
          await sleep(10 * 1000);
        }
      } catch (e) {
        console.error(e);
        console.error(`Failed to validate layer ${layerId}, retrying...`);
        await sleep(1000);
      }
      console.log(`Done validating layer ${layerId}`);
    }

    console.log("Done.");
    this.status = TestStatus.FINISHED;
    return TestStatus.FINISHED;
  }

  private async cleanup() {
    if (this.containers) {
      console.log("Cleaning up containers");
      await tearDown(this.containers);
    }
  }

  async verifyLayer(
    ingress: restate.Ingress,
    layerId: number
  ): Promise<boolean> {
    console.log(`Trying to verify layer ${layerId}`);

    const layer = this.stateTracker.getLayer(layerId);
    const interpreterLn = createInterpreterObject(layerId);

    for (const layerChunk of batch(iterate(layer), 256)) {
      const futures = layerChunk.map(async ({ expected, id }) => {
        const actual = await retry(
          async () =>
            await ingress.objectClient(interpreterLn, `${id}`).counter()
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

const InterpreterL0 = interpreterObjectForLayer(0);
