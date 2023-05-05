import * as restate from "@restatedev/restate-sdk";

import { protoMetadata as counterProtoMetadata } from "./generated/counter";
import { protoMetadata as noopProtoMetadata } from "./generated/noop";
import { protoMetadata as coordinatorProtoMetadata } from "./generated/coordinator";
import { protoMetadata as receiverProtoMetadata } from "./generated/receiver";
import { protoMetadata as listProtoMetadata } from "./generated/list";
import { protoMetadata as errorsProtoMetadata } from "./generated/errors";
import { protoMetadata as nonDeterminismProtoMetadata } from "./generated/non_determinism";
import { CounterService, CounterServiceFQN } from "./counter";
import { ListService, ListServiceFQN } from "./collections";
import { FailingService, FailingServiceFQN } from "./errors";
import { NoopService, NoopServiceFQN } from "./noop";
import { CoordinatorService, CoordinatorServiceFQN } from "./coordinator";
import { ReceiverService, ReceiverServiceFQN } from "./receiver";
import {
  NonDeterministicService,
  NonDeterministicServiceFQN,
} from "./non_determinism";

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
    NoopServiceFQN,
    {
      descriptor: noopProtoMetadata,
      service: "Noop",
      instance: new NoopService(),
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
