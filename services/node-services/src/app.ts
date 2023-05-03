import * as restate from "@restatedev/restate-sdk";

import { protoMetadata as counterProtoMetadata } from "./generated/counter";
import { protoMetadata as noopProtoMetadata } from "./generated/noop";
import { protoMetadata as coordinatorProtoMetadata } from "./generated/coordinator";
import { protoMetadata as receiverProtoMetadata } from "./generated/receiver";
import { CounterService, CounterServiceFQN } from "./counter";
import { NoopService, NoopServiceFQN } from "./noop";
import { CoordinatorService, CoordinatorServiceFQN } from "./coordinator";
import { ServiceOpts } from "@restatedev/restate-sdk/dist/restate";
import { ReceiverService, ReceiverServiceFQN } from "./receiver";

let serverBuilder = restate.createServer();

const services = new Map<string, ServiceOpts>([
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

serverBuilder.listen(parseInt(process.env.PORT ?? "8080"));
