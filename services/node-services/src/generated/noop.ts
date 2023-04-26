/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "counter";

export interface Noop {
  doAndReportInvocationCount(request: Empty): Promise<Empty>;
}

export class NoopClientImpl implements Noop {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "counter.Noop";
    this.rpc = rpc;
    this.doAndReportInvocationCount = this.doAndReportInvocationCount.bind(this);
  }
  doAndReportInvocationCount(request: Empty): Promise<Empty> {
    const data = Empty.encode(request).finish();
    const promise = this.rpc.request(this.service, "DoAndReportInvocationCount", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }
}

interface Rpc {
  request(service: string, method: string, data: Uint8Array): Promise<Uint8Array>;
}

type ProtoMetaMessageOptions = {
  options?: { [key: string]: any };
  fields?: { [key: string]: { [key: string]: any } };
  oneof?: { [key: string]: { [key: string]: any } };
  nested?: { [key: string]: ProtoMetaMessageOptions };
};

export interface ProtoMetadata {
  fileDescriptor: FileDescriptorProto1;
  references: { [key: string]: any };
  dependencies?: ProtoMetadata[];
  options?: {
    options?: { [key: string]: any };
    services?: {
      [key: string]: { options?: { [key: string]: any }; methods?: { [key: string]: { [key: string]: any } } };
    };
    messages?: { [key: string]: ProtoMetaMessageOptions };
    enums?: { [key: string]: { options?: { [key: string]: any }; values?: { [key: string]: { [key: string]: any } } } };
  };
}

export const protoMetadata: ProtoMetadata = {
  fileDescriptor: FileDescriptorProto1.fromPartial({
    "name": "noop.proto",
    "package": "counter",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [],
    "enumType": [],
    "service": [{
      "name": "Noop",
      "method": [{
        "name": "DoAndReportInvocationCount",
        "inputType": ".google.protobuf.Empty",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.counter",
      "javaOuterClassname": "NoopProto",
      "javaMultipleFiles": true,
      "javaGenerateEqualsAndHash": false,
      "javaStringCheckUtf8": false,
      "optimizeFor": 1,
      "goPackage": "",
      "ccGenericServices": false,
      "javaGenericServices": false,
      "pyGenericServices": false,
      "phpGenericServices": false,
      "deprecated": false,
      "ccEnableArenas": false,
      "objcClassPrefix": "CXX",
      "csharpNamespace": "Counter",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Counter",
      "phpMetadataNamespace": "Counter\\GPBMetadata",
      "rubyPackage": "Counter",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: { ".counter.Noop": NoopClientImpl },
  dependencies: [protoMetadata1, protoMetadata2],
  options: { services: { "Noop": { options: { "service_type": 0 }, methods: {} } } },
};
