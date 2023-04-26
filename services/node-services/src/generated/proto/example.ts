/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata1 } from "./dev/restate/ext";

export const protobufPackage = "org.example";

export interface SampleRequest {
  request: string;
}

export interface SampleResponse {
  response: string;
}

function createBaseSampleRequest(): SampleRequest {
  return { request: "" };
}

export const SampleRequest = {
  encode(message: SampleRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.request !== "") {
      writer.uint32(10).string(message.request);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): SampleRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseSampleRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.request = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): SampleRequest {
    return { request: isSet(object.request) ? String(object.request) : "" };
  },

  toJSON(message: SampleRequest): unknown {
    const obj: any = {};
    message.request !== undefined && (obj.request = message.request);
    return obj;
  },

  create(base?: DeepPartial<SampleRequest>): SampleRequest {
    return SampleRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<SampleRequest>): SampleRequest {
    const message = createBaseSampleRequest();
    message.request = object.request ?? "";
    return message;
  },
};

function createBaseSampleResponse(): SampleResponse {
  return { response: "" };
}

export const SampleResponse = {
  encode(message: SampleResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.response !== "") {
      writer.uint32(10).string(message.response);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): SampleResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseSampleResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.response = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): SampleResponse {
    return { response: isSet(object.response) ? String(object.response) : "" };
  },

  toJSON(message: SampleResponse): unknown {
    const obj: any = {};
    message.response !== undefined && (obj.response = message.response);
    return obj;
  },

  create(base?: DeepPartial<SampleResponse>): SampleResponse {
    return SampleResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<SampleResponse>): SampleResponse {
    const message = createBaseSampleResponse();
    message.response = object.response ?? "";
    return message;
  },
};

export interface ExampleService {
  sampleCall(request: SampleRequest): Promise<SampleResponse>;
}

export class ExampleServiceClientImpl implements ExampleService {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "org.example.ExampleService";
    this.rpc = rpc;
    this.sampleCall = this.sampleCall.bind(this);
  }
  sampleCall(request: SampleRequest): Promise<SampleResponse> {
    const data = SampleRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "SampleCall", data);
    return promise.then((data) => SampleResponse.decode(_m0.Reader.create(data)));
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
    "name": "example.proto",
    "package": "org.example",
    "dependency": ["dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "SampleRequest",
      "field": [{
        "name": "request",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "request",
        "options": undefined,
        "proto3Optional": false,
      }],
      "extension": [],
      "nestedType": [],
      "enumType": [],
      "extensionRange": [],
      "oneofDecl": [],
      "options": undefined,
      "reservedRange": [],
      "reservedName": [],
    }, {
      "name": "SampleResponse",
      "field": [{
        "name": "response",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "response",
        "options": undefined,
        "proto3Optional": false,
      }],
      "extension": [],
      "nestedType": [],
      "enumType": [],
      "extensionRange": [],
      "oneofDecl": [],
      "options": undefined,
      "reservedRange": [],
      "reservedName": [],
    }],
    "enumType": [],
    "service": [{
      "name": "ExampleService",
      "method": [{
        "name": "SampleCall",
        "inputType": ".org.example.SampleRequest",
        "outputType": ".org.example.SampleResponse",
        "options": { "deprecated": false, "idempotencyLevel": 0, "uninterpretedOption": [] },
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.org.example",
      "javaOuterClassname": "ExampleProto",
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
      "objcClassPrefix": "OEX",
      "csharpNamespace": "Org.Example",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Org\\Example",
      "phpMetadataNamespace": "Org\\Example\\GPBMetadata",
      "rubyPackage": "Org::Example",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".org.example.SampleRequest": SampleRequest,
    ".org.example.SampleResponse": SampleResponse,
    ".org.example.ExampleService": ExampleServiceClientImpl,
  },
  dependencies: [protoMetadata1],
  options: { services: { "ExampleService": { options: { "service_type": 0 }, methods: {} } } },
};

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends Array<infer U> ? Array<DeepPartial<U>> : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}
