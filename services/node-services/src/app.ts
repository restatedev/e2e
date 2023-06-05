import * as restate from "@restatedev/restate-sdk";

import { protoMetadata as counterProtoMetadata } from "./generated/counter";
import { protoMetadata as coordinatorProtoMetadata } from "./generated/coordinator";
import { protoMetadata as receiverProtoMetadata } from "./generated/receiver";
import { protoMetadata as listProtoMetadata } from "./generated/list";
import { protoMetadata as errorsProtoMetadata } from "./generated/errors";
import { protoMetadata as nonDeterminismProtoMetadata } from "./generated/non_determinism";
import { protoMetadata as verifierProtoMetadata } from "./generated/verifier";
import { protoMetadata as interpreterProtoMetadata } from "./generated/interpreter";
import { protoMetadata as sideEffectProtoMetadata } from "./generated/side_effect";
import { CounterService, CounterServiceFQN } from "./counter";
import { ListService, ListServiceFQN } from "./collections";
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

let serverBuilder = restate.createServer();

const services = new Map<string, restate.ServiceOpts>([
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
]);

console.log(services.keys());

if (process.env.SERVICES === undefined) {
  throw new Error("Cannot find SERVICES env");
}
const servicesEnv = process.env.SERVICES.split(",");
console.log("Services to mount: " + servicesEnv);
for (let service of servicesEnv) {
  service = service.trim();
  const foundService = services.get(service);
  if (foundService == undefined) {
    throw new Error("Unknown service '" + service + "'");
  } else {
    console.log("Mounting " + service);
    serverBuilder = serverBuilder.bindService(foundService);
  }
}

serverBuilder.listen();
