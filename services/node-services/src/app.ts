import * as restate from "@restatedev/restate-sdk";

import {
  protoMetadata as counterProtoMetadata,
} from "./generated/counter";
import {
  CounterService,
  CounterServiceFQN
} from "./counter";
import { ServiceOpts } from "@restatedev/restate-sdk/dist/restate";

var serverBuilder = restate.createServer()

const services = new Map<string, ServiceOpts>([
  [CounterServiceFQN, { descriptor: counterProtoMetadata, service: "Counter", instance: new CounterService() }]
]);

console.log(services.keys())

if (process.env.SERVICES === undefined) {
  throw new Error("Cannot find SERVICES env")
}
let servicesEnv = process.env.SERVICES!.split(",");
console.log("Services to mount: " + servicesEnv)
for (let service of servicesEnv) {
  service = service.trim()
  if (!services.has(service)) {
    throw new Error("Unknown service '" + service + "'")
  }
  console.log("Mounting " + service)
  serverBuilder = serverBuilder.bindService(services.get(service)!);
}

serverBuilder.listen(parseInt(process.env.PORT ?? "8080"));
