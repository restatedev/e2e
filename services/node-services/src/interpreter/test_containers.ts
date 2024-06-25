// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import {
  GenericContainer,
  Network,
  StartedNetwork,
  StartedTestContainer,
} from "testcontainers";

export interface EnvironmentSpec {
  restate: {
    image: string;
    env: Record<string, string>;
    pull?: boolean;
  };

  interpreters: {
    image: string;
    env: Record<string, string>;
    pull?: boolean;
  };

  service: {
    image: string;
    env: Record<string, string>;
    pull?: boolean;
  };
}

export interface TestEnvironment {
  ingressUrl: string;
  adminUrl: string;
  services: string[];
  containers: Containers;
  network: StartedNetwork;
}

export interface Containers {
  restateContainer: StartedTestContainer;
  interpreterZeroContainer: StartedTestContainer;
  interpreterOneContainer: StartedTestContainer;
  interpreterTwoContainer: StartedTestContainer;
  servicesContainer: StartedTestContainer;
}

export async function setupContainers(
  env: EnvironmentSpec
): Promise<TestEnvironment> {
  console.log(env);

  const network = await new Network().start();

  const restate = new GenericContainer(env.restate.image)
    .withExposedPorts(8080, 9070)
    .withNetwork(network)
    .withNetworkAliases("restate")
    .withPullPolicy({
      shouldPull() {
        return env.restate.pull ?? true;
      },
    })
    .withEnvironment({
      RESTATE_LOG_FILTER: "restate=warn",
      RESTATE_LOG_FORMAT: "json",
      ...(env.restate?.env ?? {}),
    })
    .withUlimits({
      nproc: { soft: 65535, hard: 65535 },
      nofile: { soft: 65535, hard: 65535 },
    })
    .start();

  const names = ["interpreter_zero", "interpreter_one", "interpreter_two"];
  const interpreters = [];
  for (let i = 0; i < 3; i++) {
    const port = 9000 + i;
    const auxEnv = {
      PORT: `${port}`,
      RESTATE_LOGGING: "ERROR",
      NODE_ENV: "production",
      NODE_OPTIONS: "--max-old-space-size=4096",
      SERVICES: `ObjectInterpreterL${i}`,
      ...(env.interpreters?.env ?? {}),
    };
    const interpreter = new GenericContainer(env.interpreters.image)
      .withNetwork(network)
      .withNetworkAliases(names[i])
      .withExposedPorts(port)
      .withPullPolicy({
        shouldPull() {
          return env.interpreters.pull ?? true;
        },
      })
      .withEnvironment(auxEnv)
      .withUlimits({
        nproc: { soft: 65535, hard: 65535 },
        nofile: { soft: 65535, hard: 65535 },
      })
      .start();

    interpreters.push(interpreter);
  }

  const services = new GenericContainer(env.service.image)
    .withNetwork(network)
    .withNetworkAliases("services")
    .withPullPolicy({
      shouldPull() {
        return env.service.pull ?? true;
      },
    })
    .withExposedPorts(9003)
    .withEnvironment({
      PORT: "9003",
      RESTATE_LOGGING: "ERROR",
      NODE_ENV: "production",
      NODE_OPTIONS: "--max-old-space-size=4096",
      SERVICES: "ServiceInterpreterHelper",
      ...(env.service?.env ?? {}),
    })
    .withUlimits({
      nproc: { soft: 65535, hard: 65535 },
      nofile: { soft: 65535, hard: 65535 },
    })
    .start();

  const restateContainer = await restate;
  const interpreterZeroContainer = await interpreters[0];
  const interpreterOneContainer = await interpreters[1];
  const interpreterTwoContainer = await interpreters[2];
  const servicesContainer = await services;

  const ingressUrl = `http://${restateContainer.getHost()}:${restateContainer.getMappedPort(
    8080
  )}`;
  const adminUrl = `http://${restateContainer.getHost()}:${restateContainer.getMappedPort(
    9070
  )}`;

  return {
    ingressUrl,
    adminUrl,
    services: [
      "http://interpreter_zero:9000",
      "http://interpreter_one:9001",
      "http://interpreter_two:9002",
      "http://services:9003",
    ],
    containers: {
      restateContainer,
      interpreterZeroContainer,
      interpreterOneContainer,
      interpreterTwoContainer,
      servicesContainer,
    },
    network,
  };
}

export async function tearDown(env: TestEnvironment): Promise<void> {
  const futures = [
    env.containers.restateContainer.stop(),
    env.containers.interpreterZeroContainer.stop(),
    env.containers.interpreterOneContainer.stop(),
    env.containers.interpreterTwoContainer.stop(),
    env.containers.servicesContainer.stop(),
  ];

  await Promise.all(futures);
  await env.network.stop();
}
