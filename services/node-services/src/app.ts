import * as restate from "@restatedev/restate-sdk";

import {
  ExampleService,
  SampleRequest,
  SampleResponse,
  protoMetadata,
} from "./generated/proto/example";


export class MyExampleService implements ExampleService {

  async sampleCall(request: SampleRequest): Promise<SampleResponse> {
    return SampleResponse.create({response: "Hello " + request.request});
  }
}

restate
  .createServer()
  .bindService({
    descriptor: protoMetadata,
    service: "ExampleService",
    instance: new MyExampleService(),
  })
  .listen(parseInt(process.env.PORT ?? "8080"));
