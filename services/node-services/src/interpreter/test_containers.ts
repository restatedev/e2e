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
  PullPolicy,
  StartedNetwork,
  StartedTestContainer,
} from "testcontainers";

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

export async function setupContainers(): Promise<TestEnvironment> {
  const network = await new Network().start();

  const restate = new GenericContainer("ghcr.io/restatedev/restate:main")
    .withExposedPorts(8080, 9070)
    .withNetwork(network)
    .withNetworkAliases("restate")
    .withPullPolicy(PullPolicy.alwaysPull())
    .withEnvironment({
      RESTATE_LOG_FILTER: "restate=warn",
      RESTATE_LOG_FORMAT: "json",
    })
    .withUlimits({
      nproc: { soft: 65535, hard: 65535 },
      nofile: { soft: 65535, hard: 65535 },
    })
    .start();

  const zero = new GenericContainer("ghcr.io/restatedev/e2e-node-services:main")
    .withNetwork(network)
    .withNetworkAliases("interpreter_zero")
    .withPullPolicy(PullPolicy.alwaysPull())
    .withEnvironment({
      PORT: "9000",
      RESTATE_LOGGING: "ERROR",
      NODE_ENV: "production",
      SERVICES: "ObjectInterpreterL0",
    })
    .start();

  const one = new GenericContainer("ghcr.io/restatedev/e2e-node-services:main")
    .withNetwork(network)
    .withNetworkAliases("interpreter_one")
    .withPullPolicy(PullPolicy.alwaysPull())

    .withExposedPorts(9001)
    .withEnvironment({
      PORT: "9001",
      RESTATE_LOGGING: "ERROR",
      NODE_ENV: "production",
      SERVICES: "ObjectInterpreterL1",
    })
    .start();

  const two = new GenericContainer("ghcr.io/restatedev/e2e-node-services:main")
    .withNetwork(network)
    .withNetworkAliases("interpreter_two")
    .withPullPolicy(PullPolicy.alwaysPull())
    .withExposedPorts(9002)
    .withEnvironment({
      PORT: "9002",
      RESTATE_LOGGING: "ERROR",
      NODE_ENV: "production",
      SERVICES: "ObjectInterpreterL2",
    })
    .start();

  const services = new GenericContainer(
    "ghcr.io/restatedev/e2e-node-services:main"
  )
    .withNetwork(network)
    .withNetworkAliases("services")
    .withPullPolicy(PullPolicy.alwaysPull())
    .withExposedPorts(9003)
    .withEnvironment({
      PORT: "9003",
      RESTATE_LOGGING: "ERROR",
      NODE_ENV: "production",
      SERVICES: "ServiceInterpreterHelper",
    })
    .start();

  const restateContainer = await restate;
  const interpreterZeroContainer = await zero;
  const interpreterOneContainer = await one;
  const interpreterTwoContainer = await two;
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
