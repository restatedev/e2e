// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

import { protoMetadata as counterProtoMetadata } from "./generated/counter";
import { protoMetadata as coordinatorProtoMetadata } from "./generated/coordinator";
import { protoMetadata as receiverProtoMetadata } from "./generated/receiver";
import { protoMetadata as listProtoMetadata } from "./generated/list";
import { protoMetadata as mapProtoMetadata } from "./generated/map";
import { protoMetadata as errorsProtoMetadata } from "./generated/errors";
import { protoMetadata as nonDeterminismProtoMetadata } from "./generated/non_determinism";
import { protoMetadata as verifierProtoMetadata } from "./generated/verifier";
import { protoMetadata as interpreterProtoMetadata } from "./generated/interpreter";
import { protoMetadata as sideEffectProtoMetadata } from "./generated/side_effect";
import { protoMetadata as proxyProtoMetadata } from "./generated/proxy";
import { protoMetadata as rngProtoMetadata } from "./generated/rng";
import { protoMetadata as awakeableHolderProtoMetadata } from "./generated/awakeable_holder";
import { protoMetadata as eventHandlerProtoMetadata } from "./generated/event_handler";
import { protoMetadata as killTestProtoMetadata } from "./generated/kill_test";
import { protoMetadata as cancelTestProtoMetadata } from "./generated/cancel_test";
import { CounterService, CounterServiceFQN } from "./counter";
import { ListService, ListServiceFQN } from "./list";
import { MapService, MapServiceFQN } from "./map";
import { FailingService, FailingServiceFQN } from "./errors";
import { ProxyCounterService, ProxyCounterServiceFQN } from "./proxy_counter";
import { CoordinatorService, CoordinatorServiceFQN } from "./coordinator";
import { ReceiverService, ReceiverServiceFQN } from "./receiver";
import {
  NonDeterministicService,
  NonDeterministicServiceFQN,
} from "./non_determinism";
import { CommandVerifierService, CommandVerifierServiceFQN } from "./verifier";
import {
  CommandInterpreterService,
  CommandInterpreterServiceFQN,
} from "./interpreter";
import { SideEffectService, SideEffectServiceFQN } from "./side_effect";
import { ProxyService, ProxyServiceFQN } from "./proxy";
import {
  RandomNumberListGeneratorService,
  RandomNumberListGeneratorServiceFQN,
} from "./random_number_generator";
import {
  AwakeableHolderService,
  AwakeableHolderServiceFQN,
} from "./awakeable_holder";
import {
  HandlerAPIEchoTestFQN,
  HandlerAPIEchoRouter,
  CounterHandlerAPIFQN,
  CounterHandlerAPIRouter,
} from "./handler_api";
import { EventHandlerFQN, EventHandlerService } from "./event_handler";
import { startEmbeddedHandlerServer } from "./embedded_handler_api";
import {
  KillSingletonServiceFQN,
  KillTestService,
  KillTestServiceFQN,
  KillSingletonService,
} from "./kill_test";
import {
  BlockingServiceFQN,
  CancelTestService,
  CancelTestServiceFQN,
  BlockingService,
} from "./cancel_test";

let serverBuilder;
export let handler: (event: any) => Promise<any>;
if (process.env.AWS_LAMBDA_FUNCTION_NAME) {
  serverBuilder = restate.createLambdaApiGatewayHandler();
} else {
  serverBuilder = restate.createServer();
}

const services = new Map<
  string,
  restate.ServiceOpts | { router: any } | { keyedRouter: any }
>([
  [
    CounterServiceFQN,
    {
      descriptor: counterProtoMetadata,
      service: "Counter",
      instance: new CounterService(),
    },
  ],
  [
    ProxyCounterServiceFQN,
    {
      descriptor: counterProtoMetadata,
      service: "ProxyCounter",
      instance: new ProxyCounterService(),
    },
  ],
  [
    CoordinatorServiceFQN,
    {
      descriptor: coordinatorProtoMetadata,
      service: "Coordinator",
      instance: new CoordinatorService(),
    },
  ],
  [
    ReceiverServiceFQN,
    {
      descriptor: receiverProtoMetadata,
      service: "Receiver",
      instance: new ReceiverService(),
    },
  ],
  [
    ListServiceFQN,
    {
      descriptor: listProtoMetadata,
      service: "ListService",
      instance: new ListService(),
    },
  ],
  [
    NonDeterministicServiceFQN,
    {
      descriptor: nonDeterminismProtoMetadata,
      service: "NonDeterministicService",
      instance: new NonDeterministicService(),
    },
  ],
  [
    FailingServiceFQN,
    {
      descriptor: errorsProtoMetadata,
      service: "FailingService",
      instance: new FailingService(),
    },
  ],
  [
    CommandVerifierServiceFQN,
    {
      descriptor: verifierProtoMetadata,
      service: "CommandVerifier",
      instance: new CommandVerifierService(),
    },
  ],
  [
    CommandInterpreterServiceFQN,
    {
      descriptor: interpreterProtoMetadata,
      service: "CommandInterpreter",
      instance: new CommandInterpreterService(),
    },
  ],
  [
    SideEffectServiceFQN,
    {
      descriptor: sideEffectProtoMetadata,
      service: "SideEffect",
      instance: new SideEffectService(),
    },
  ],
  [
    ProxyServiceFQN,
    {
      descriptor: proxyProtoMetadata,
      service: "ProxyService",
      instance: new ProxyService(),
    },
  ],
  [
    RandomNumberListGeneratorServiceFQN,
    {
      descriptor: rngProtoMetadata,
      service: "RandomNumberListGenerator",
      instance: new RandomNumberListGeneratorService(),
    },
  ],
  [
    AwakeableHolderServiceFQN,
    {
      descriptor: awakeableHolderProtoMetadata,
      service: "AwakeableHolderService",
      instance: new AwakeableHolderService(),
    },
  ],
  [
    HandlerAPIEchoTestFQN,
    {
      router: HandlerAPIEchoRouter,
    },
  ],
  [
    CounterHandlerAPIFQN,
    {
      keyedRouter: CounterHandlerAPIRouter,
    },
  ],
  [
    EventHandlerFQN,
    {
      descriptor: eventHandlerProtoMetadata,
      service: "EventHandler",
      instance: new EventHandlerService(),
    },
  ],
  [
    KillTestServiceFQN,
    {
      descriptor: killTestProtoMetadata,
      service: "KillTestService",
      instance: new KillTestService(),
    },
  ],
  [
    KillSingletonServiceFQN,
    {
      descriptor: killTestProtoMetadata,
      service: "KillSingletonService",
      instance: new KillSingletonService(),
    },
  ],
  [
    CancelTestServiceFQN,
    {
      descriptor: cancelTestProtoMetadata,
      service: "CancelTestService",
      instance: new CancelTestService(),
    },
  ],
  [
    BlockingServiceFQN,
    {
      descriptor: cancelTestProtoMetadata,
      service: "BlockingService",
      instance: new BlockingService(),
    },
  ],
  [
    MapServiceFQN,
    {
      descriptor: mapProtoMetadata,
      service: "MapService",
      instance: new MapService(),
    },
  ],
]);
console.log("Known services: " + services.keys());

if (!process.env.SERVICES && !process.env.EMBEDDED_HANDLER_PORT) {
  throw new Error("Cannot find SERVICES nor EMBEDDED_HANDLER_PORT env");
}

// If SERVICES env is set, start the restate services
if (process.env.SERVICES) {
  const servicesEnv = process.env.SERVICES.split(",");
  console.log("Services to mount: " + servicesEnv);
  for (let service of servicesEnv) {
    service = service.trim();
    const foundService = services.get(service);
    if (foundService === undefined) {
      throw new Error("Unknown service '" + service + "'");
    } else if ((foundService as restate.ServiceOpts).descriptor !== undefined) {
      console.log("Mounting " + service);
      serverBuilder = serverBuilder.bindService(
        foundService as restate.ServiceOpts
      );
    } else if (
      (foundService as restate.UnKeyedRouter<any>).router !== undefined
    ) {
      console.log("Mounting router " + service);
      serverBuilder = serverBuilder.bindRouter(
        service,
        (foundService as { router: any }).router
      );
    } else {
      console.log("Mounting keyed router " + service);
      serverBuilder = serverBuilder.bindKeyedRouter(
        service,
        (foundService as { keyedRouter: any }).keyedRouter
      );
    }
  }

  if ("handle" in serverBuilder) {
    handler = serverBuilder.handle();
  } else {
    serverBuilder.listen();
  }
}

if (process.env.EMBEDDED_HANDLER_PORT) {
  startEmbeddedHandlerServer(parseInt(process.env.EMBEDDED_HANDLER_PORT));
}
