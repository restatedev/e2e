/* eslint-disable */
import Long from "long";
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "singleton_counter";

export interface CounterNumber {
  value: number;
}

export interface CounterUpdateResult {
  oldValue: number;
  newValue: number;
}

function createBaseCounterNumber(): CounterNumber {
  return { value: 0 };
}

export const CounterNumber = {
  encode(message: CounterNumber, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.value !== 0) {
      writer.uint32(16).int64(message.value);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): CounterNumber {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCounterNumber();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
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

  fromJSON(object: any): CounterNumber {
    return { value: isSet(object.value) ? Number(object.value) : 0 };
  },

  toJSON(message: CounterNumber): unknown {
    const obj: any = {};
    message.value !== undefined && (obj.value = Math.round(message.value));
    return obj;
  },

  create(base?: DeepPartial<CounterNumber>): CounterNumber {
    return CounterNumber.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<CounterNumber>): CounterNumber {
    const message = createBaseCounterNumber();
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

export interface SingletonCounter {
  reset(request: Empty): Promise<Empty>;
  add(request: CounterNumber): Promise<Empty>;
  get(request: Empty): Promise<CounterNumber>;
}

export class SingletonCounterClientImpl implements SingletonCounter {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "singleton_counter.SingletonCounter";
    this.rpc = rpc;
    this.reset = this.reset.bind(this);
    this.add = this.add.bind(this);
    this.get = this.get.bind(this);
  }
  reset(request: Empty): Promise<Empty> {
    const data = Empty.encode(request).finish();
    const promise = this.rpc.request(this.service, "Reset", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  add(request: CounterNumber): Promise<Empty> {
    const data = CounterNumber.encode(request).finish();
    const promise = this.rpc.request(this.service, "Add", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  get(request: Empty): Promise<CounterNumber> {
    const data = Empty.encode(request).finish();
    const promise = this.rpc.request(this.service, "Get", data);
    return promise.then((data) => CounterNumber.decode(_m0.Reader.create(data)));
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
    "name": "singleton_counter.proto",
    "package": "singleton_counter",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "CounterNumber",
      "field": [{
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
      "name": "SingletonCounter",
      "method": [{
        "name": "Reset",
        "inputType": ".google.protobuf.Empty",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Add",
        "inputType": ".singleton_counter.CounterNumber",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Get",
        "inputType": ".google.protobuf.Empty",
        "outputType": ".singleton_counter.CounterNumber",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.singleton_counter",
      "javaOuterClassname": "SingletonCounterProto",
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
      "objcClassPrefix": "SXX",
      "csharpNamespace": "SingletonCounter",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "SingletonCounter",
      "phpMetadataNamespace": "SingletonCounter\\GPBMetadata",
      "rubyPackage": "SingletonCounter",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".singleton_counter.CounterNumber": CounterNumber,
    ".singleton_counter.CounterUpdateResult": CounterUpdateResult,
    ".singleton_counter.SingletonCounter": SingletonCounterClientImpl,
  },
  dependencies: [protoMetadata1, protoMetadata2],
  options: { services: { "SingletonCounter": { options: { "service_type": 2 }, methods: {} } } },
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
