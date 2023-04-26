/* eslint-disable */
import Long from "long";
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "counter";

export interface CounterRequest {
  counterName: string;
}

export interface CounterAddRequest {
  counterName: string;
  value: number;
}

export interface GetResponse {
  value: number;
}

export interface CounterUpdateResult {
  oldValue: number;
  newValue: number;
}

function createBaseCounterRequest(): CounterRequest {
  return { counterName: "" };
}

export const CounterRequest = {
  encode(message: CounterRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.counterName !== "") {
      writer.uint32(10).string(message.counterName);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): CounterRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCounterRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.counterName = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CounterRequest {
    return { counterName: isSet(object.counterName) ? String(object.counterName) : "" };
  },

  toJSON(message: CounterRequest): unknown {
    const obj: any = {};
    message.counterName !== undefined && (obj.counterName = message.counterName);
    return obj;
  },

  create(base?: DeepPartial<CounterRequest>): CounterRequest {
    return CounterRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<CounterRequest>): CounterRequest {
    const message = createBaseCounterRequest();
    message.counterName = object.counterName ?? "";
    return message;
  },
};

function createBaseCounterAddRequest(): CounterAddRequest {
  return { counterName: "", value: 0 };
}

export const CounterAddRequest = {
  encode(message: CounterAddRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.counterName !== "") {
      writer.uint32(10).string(message.counterName);
    }
    if (message.value !== 0) {
      writer.uint32(16).int64(message.value);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): CounterAddRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCounterAddRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.counterName = reader.string();
          continue;
        case 2:
          if (tag != 16) {
            break;
          }

          message.value = longToNumber(reader.int64() as Long);
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CounterAddRequest {
    return {
      counterName: isSet(object.counterName) ? String(object.counterName) : "",
      value: isSet(object.value) ? Number(object.value) : 0,
    };
  },

  toJSON(message: CounterAddRequest): unknown {
    const obj: any = {};
    message.counterName !== undefined && (obj.counterName = message.counterName);
    message.value !== undefined && (obj.value = Math.round(message.value));
    return obj;
  },

  create(base?: DeepPartial<CounterAddRequest>): CounterAddRequest {
    return CounterAddRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<CounterAddRequest>): CounterAddRequest {
    const message = createBaseCounterAddRequest();
    message.counterName = object.counterName ?? "";
    message.value = object.value ?? 0;
    return message;
  },
};

function createBaseGetResponse(): GetResponse {
  return { value: 0 };
}

export const GetResponse = {
  encode(message: GetResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.value !== 0) {
      writer.uint32(8).int64(message.value);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): GetResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseGetResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.value = longToNumber(reader.int64() as Long);
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): GetResponse {
    return { value: isSet(object.value) ? Number(object.value) : 0 };
  },

  toJSON(message: GetResponse): unknown {
    const obj: any = {};
    message.value !== undefined && (obj.value = Math.round(message.value));
    return obj;
  },

  create(base?: DeepPartial<GetResponse>): GetResponse {
    return GetResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<GetResponse>): GetResponse {
    const message = createBaseGetResponse();
    message.value = object.value ?? 0;
    return message;
  },
};

function createBaseCounterUpdateResult(): CounterUpdateResult {
  return { oldValue: 0, newValue: 0 };
}

export const CounterUpdateResult = {
  encode(message: CounterUpdateResult, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.oldValue !== 0) {
      writer.uint32(8).int64(message.oldValue);
    }
    if (message.newValue !== 0) {
      writer.uint32(16).int64(message.newValue);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): CounterUpdateResult {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCounterUpdateResult();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.oldValue = longToNumber(reader.int64() as Long);
          continue;
        case 2:
          if (tag != 16) {
            break;
          }

          message.newValue = longToNumber(reader.int64() as Long);
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CounterUpdateResult {
    return {
      oldValue: isSet(object.oldValue) ? Number(object.oldValue) : 0,
      newValue: isSet(object.newValue) ? Number(object.newValue) : 0,
    };
  },

  toJSON(message: CounterUpdateResult): unknown {
    const obj: any = {};
    message.oldValue !== undefined && (obj.oldValue = Math.round(message.oldValue));
    message.newValue !== undefined && (obj.newValue = Math.round(message.newValue));
    return obj;
  },

  create(base?: DeepPartial<CounterUpdateResult>): CounterUpdateResult {
    return CounterUpdateResult.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<CounterUpdateResult>): CounterUpdateResult {
    const message = createBaseCounterUpdateResult();
    message.oldValue = object.oldValue ?? 0;
    message.newValue = object.newValue ?? 0;
    return message;
  },
};

export interface Counter {
  reset(request: CounterRequest): Promise<Empty>;
  add(request: CounterAddRequest): Promise<Empty>;
  addThenFail(request: CounterAddRequest): Promise<Empty>;
  get(request: CounterRequest): Promise<GetResponse>;
  getAndAdd(request: CounterAddRequest): Promise<CounterUpdateResult>;
}

export class CounterClientImpl implements Counter {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "counter.Counter";
    this.rpc = rpc;
    this.reset = this.reset.bind(this);
    this.add = this.add.bind(this);
    this.addThenFail = this.addThenFail.bind(this);
    this.get = this.get.bind(this);
    this.getAndAdd = this.getAndAdd.bind(this);
  }
  reset(request: CounterRequest): Promise<Empty> {
    const data = CounterRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Reset", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  add(request: CounterAddRequest): Promise<Empty> {
    const data = CounterAddRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Add", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  addThenFail(request: CounterAddRequest): Promise<Empty> {
    const data = CounterAddRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "AddThenFail", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  get(request: CounterRequest): Promise<GetResponse> {
    const data = CounterRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Get", data);
    return promise.then((data) => GetResponse.decode(_m0.Reader.create(data)));
  }

  getAndAdd(request: CounterAddRequest): Promise<CounterUpdateResult> {
    const data = CounterAddRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "GetAndAdd", data);
    return promise.then((data) => CounterUpdateResult.decode(_m0.Reader.create(data)));
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
    "name": "counter.proto",
    "package": "counter",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "CounterRequest",
      "field": [{
        "name": "counter_name",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "counterName",
        "options": {
          "ctype": 0,
          "packed": false,
          "jstype": 0,
          "lazy": false,
          "deprecated": false,
          "weak": false,
          "uninterpretedOption": [],
        },
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
      "name": "CounterAddRequest",
      "field": [{
        "name": "counter_name",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "counterName",
        "options": {
          "ctype": 0,
          "packed": false,
          "jstype": 0,
          "lazy": false,
          "deprecated": false,
          "weak": false,
          "uninterpretedOption": [],
        },
        "proto3Optional": false,
      }, {
        "name": "value",
        "number": 2,
        "label": 1,
        "type": 3,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "value",
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
      "name": "GetResponse",
      "field": [{
        "name": "value",
        "number": 1,
        "label": 1,
        "type": 3,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "value",
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
      "name": "CounterUpdateResult",
      "field": [{
        "name": "old_value",
        "number": 1,
        "label": 1,
        "type": 3,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "oldValue",
        "options": undefined,
        "proto3Optional": false,
      }, {
        "name": "new_value",
        "number": 2,
        "label": 1,
        "type": 3,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "newValue",
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
      "name": "Counter",
      "method": [{
        "name": "Reset",
        "inputType": ".counter.CounterRequest",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Add",
        "inputType": ".counter.CounterAddRequest",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "AddThenFail",
        "inputType": ".counter.CounterAddRequest",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Get",
        "inputType": ".counter.CounterRequest",
        "outputType": ".counter.GetResponse",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "GetAndAdd",
        "inputType": ".counter.CounterAddRequest",
        "outputType": ".counter.CounterUpdateResult",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.counter",
      "javaOuterClassname": "CounterProto",
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
  references: {
    ".counter.CounterRequest": CounterRequest,
    ".counter.CounterAddRequest": CounterAddRequest,
    ".counter.GetResponse": GetResponse,
    ".counter.CounterUpdateResult": CounterUpdateResult,
    ".counter.Counter": CounterClientImpl,
  },
  dependencies: [protoMetadata1, protoMetadata2],
  options: {
    messages: {
      "CounterRequest": { fields: { "counter_name": { "field": 0 } } },
      "CounterAddRequest": { fields: { "counter_name": { "field": 0 } } },
    },
    services: { "Counter": { options: { "service_type": 1 }, methods: {} } },
  },
};

declare var self: any | undefined;
declare var window: any | undefined;
declare var global: any | undefined;
var tsProtoGlobalThis: any = (() => {
  if (typeof globalThis !== "undefined") {
    return globalThis;
  }
  if (typeof self !== "undefined") {
    return self;
  }
  if (typeof window !== "undefined") {
    return window;
  }
  if (typeof global !== "undefined") {
    return global;
  }
  throw "Unable to locate global object";
})();

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends Array<infer U> ? Array<DeepPartial<U>> : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

function longToNumber(long: Long): number {
  if (long.gt(Number.MAX_SAFE_INTEGER)) {
    throw new tsProtoGlobalThis.Error("Value is larger than Number.MAX_SAFE_INTEGER");
  }
  return long.toNumber();
}

if (_m0.util.Long !== Long) {
  _m0.util.Long = Long as any;
  _m0.configure();
}

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}
