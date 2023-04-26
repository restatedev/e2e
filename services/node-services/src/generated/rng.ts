/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata1 } from "./dev/restate/ext";

export const protobufPackage = "restate.e2e.externalcall.rnlg";

export interface GenerateNumbersRequest {
  itemsNumber: number;
}

export interface GenerateNumbersResponse {
  numbers: number[];
}

function createBaseGenerateNumbersRequest(): GenerateNumbersRequest {
  return { itemsNumber: 0 };
}

export const GenerateNumbersRequest = {
  encode(message: GenerateNumbersRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.itemsNumber !== 0) {
      writer.uint32(8).uint32(message.itemsNumber);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): GenerateNumbersRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseGenerateNumbersRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.itemsNumber = reader.uint32();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): GenerateNumbersRequest {
    return { itemsNumber: isSet(object.itemsNumber) ? Number(object.itemsNumber) : 0 };
  },

  toJSON(message: GenerateNumbersRequest): unknown {
    const obj: any = {};
    message.itemsNumber !== undefined && (obj.itemsNumber = Math.round(message.itemsNumber));
    return obj;
  },

  create(base?: DeepPartial<GenerateNumbersRequest>): GenerateNumbersRequest {
    return GenerateNumbersRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<GenerateNumbersRequest>): GenerateNumbersRequest {
    const message = createBaseGenerateNumbersRequest();
    message.itemsNumber = object.itemsNumber ?? 0;
    return message;
  },
};

function createBaseGenerateNumbersResponse(): GenerateNumbersResponse {
  return { numbers: [] };
}

export const GenerateNumbersResponse = {
  encode(message: GenerateNumbersResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    writer.uint32(10).fork();
    for (const v of message.numbers) {
      writer.int32(v);
    }
    writer.ldelim();
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): GenerateNumbersResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseGenerateNumbersResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag == 8) {
            message.numbers.push(reader.int32());
            continue;
          }

          if (tag == 10) {
            const end2 = reader.uint32() + reader.pos;
            while (reader.pos < end2) {
              message.numbers.push(reader.int32());
            }

            continue;
          }

          break;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): GenerateNumbersResponse {
    return { numbers: Array.isArray(object?.numbers) ? object.numbers.map((e: any) => Number(e)) : [] };
  },

  toJSON(message: GenerateNumbersResponse): unknown {
    const obj: any = {};
    if (message.numbers) {
      obj.numbers = message.numbers.map((e) => Math.round(e));
    } else {
      obj.numbers = [];
    }
    return obj;
  },

  create(base?: DeepPartial<GenerateNumbersResponse>): GenerateNumbersResponse {
    return GenerateNumbersResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<GenerateNumbersResponse>): GenerateNumbersResponse {
    const message = createBaseGenerateNumbersResponse();
    message.numbers = object.numbers?.map((e) => e) || [];
    return message;
  },
};

export interface RandomNumberListGenerator {
  generateNumbers(request: GenerateNumbersRequest): Promise<GenerateNumbersResponse>;
}

export class RandomNumberListGeneratorClientImpl implements RandomNumberListGenerator {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "restate.e2e.externalcall.rnlg.RandomNumberListGenerator";
    this.rpc = rpc;
    this.generateNumbers = this.generateNumbers.bind(this);
  }
  generateNumbers(request: GenerateNumbersRequest): Promise<GenerateNumbersResponse> {
    const data = GenerateNumbersRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "GenerateNumbers", data);
    return promise.then((data) => GenerateNumbersResponse.decode(_m0.Reader.create(data)));
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
    "name": "rng.proto",
    "package": "restate.e2e.externalcall.rnlg",
    "dependency": ["dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "GenerateNumbersRequest",
      "field": [{
        "name": "items_number",
        "number": 1,
        "label": 1,
        "type": 13,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "itemsNumber",
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
      "name": "GenerateNumbersResponse",
      "field": [{
        "name": "numbers",
        "number": 1,
        "label": 3,
        "type": 5,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "numbers",
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
      "name": "RandomNumberListGenerator",
      "method": [{
        "name": "GenerateNumbers",
        "inputType": ".restate.e2e.externalcall.rnlg.GenerateNumbersRequest",
        "outputType": ".restate.e2e.externalcall.rnlg.GenerateNumbersResponse",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.restate.e2e.externalcall.rnlg",
      "javaOuterClassname": "RngProto",
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
      "objcClassPrefix": "REER",
      "csharpNamespace": "Restate.E2e.Externalcall.Rnlg",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Restate\\E2e\\Externalcall\\Rnlg",
      "phpMetadataNamespace": "Restate\\E2e\\Externalcall\\Rnlg\\GPBMetadata",
      "rubyPackage": "Restate::E2e::Externalcall::Rnlg",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".restate.e2e.externalcall.rnlg.GenerateNumbersRequest": GenerateNumbersRequest,
    ".restate.e2e.externalcall.rnlg.GenerateNumbersResponse": GenerateNumbersResponse,
    ".restate.e2e.externalcall.rnlg.RandomNumberListGenerator": RandomNumberListGeneratorClientImpl,
  },
  dependencies: [protoMetadata1],
  options: { services: { "RandomNumberListGenerator": { options: { "service_type": 0 }, methods: {} } } },
};

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends Array<infer U> ? Array<DeepPartial<U>> : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}
