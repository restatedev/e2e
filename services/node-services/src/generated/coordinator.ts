/* eslint-disable */
import Long from "long";
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "coordinator";

export interface Duration {
  millis: number;
}

export interface ProxyResponse {
  message: string;
}

export interface ComplexRequest {
  sleepDuration: Duration | undefined;
  requestValue: string;
}

export interface ComplexResponse {
  responseValue: string;
}

export interface TimeoutResponse {
  timeoutOccurred: boolean;
}

export interface InvokeSequentiallyRequest {
  executeAsBackgroundCall: boolean[];
}

function createBaseDuration(): Duration {
  return { millis: 0 };
}

export const Duration = {
  encode(message: Duration, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.millis !== 0) {
      writer.uint32(8).uint64(message.millis);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): Duration {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseDuration();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.millis = longToNumber(reader.uint64() as Long);
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): Duration {
    return { millis: isSet(object.millis) ? Number(object.millis) : 0 };
  },

  toJSON(message: Duration): unknown {
    const obj: any = {};
    message.millis !== undefined && (obj.millis = Math.round(message.millis));
    return obj;
  },

  create(base?: DeepPartial<Duration>): Duration {
    return Duration.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<Duration>): Duration {
    const message = createBaseDuration();
    message.millis = object.millis ?? 0;
    return message;
  },
};

function createBaseProxyResponse(): ProxyResponse {
  return { message: "" };
}

export const ProxyResponse = {
  encode(message: ProxyResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.message !== "") {
      writer.uint32(10).string(message.message);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): ProxyResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseProxyResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.message = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): ProxyResponse {
    return { message: isSet(object.message) ? String(object.message) : "" };
  },

  toJSON(message: ProxyResponse): unknown {
    const obj: any = {};
    message.message !== undefined && (obj.message = message.message);
    return obj;
  },

  create(base?: DeepPartial<ProxyResponse>): ProxyResponse {
    return ProxyResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ProxyResponse>): ProxyResponse {
    const message = createBaseProxyResponse();
    message.message = object.message ?? "";
    return message;
  },
};

function createBaseComplexRequest(): ComplexRequest {
  return { sleepDuration: undefined, requestValue: "" };
}

export const ComplexRequest = {
  encode(message: ComplexRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.sleepDuration !== undefined) {
      Duration.encode(message.sleepDuration, writer.uint32(10).fork()).ldelim();
    }
    if (message.requestValue !== "") {
      writer.uint32(18).string(message.requestValue);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): ComplexRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseComplexRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.sleepDuration = Duration.decode(reader, reader.uint32());
          continue;
        case 2:
          if (tag != 18) {
            break;
          }

          message.requestValue = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): ComplexRequest {
    return {
      sleepDuration: isSet(object.sleepDuration) ? Duration.fromJSON(object.sleepDuration) : undefined,
      requestValue: isSet(object.requestValue) ? String(object.requestValue) : "",
    };
  },

  toJSON(message: ComplexRequest): unknown {
    const obj: any = {};
    message.sleepDuration !== undefined &&
      (obj.sleepDuration = message.sleepDuration ? Duration.toJSON(message.sleepDuration) : undefined);
    message.requestValue !== undefined && (obj.requestValue = message.requestValue);
    return obj;
  },

  create(base?: DeepPartial<ComplexRequest>): ComplexRequest {
    return ComplexRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ComplexRequest>): ComplexRequest {
    const message = createBaseComplexRequest();
    message.sleepDuration = (object.sleepDuration !== undefined && object.sleepDuration !== null)
      ? Duration.fromPartial(object.sleepDuration)
      : undefined;
    message.requestValue = object.requestValue ?? "";
    return message;
  },
};

function createBaseComplexResponse(): ComplexResponse {
  return { responseValue: "" };
}

export const ComplexResponse = {
  encode(message: ComplexResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.responseValue !== "") {
      writer.uint32(10).string(message.responseValue);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): ComplexResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseComplexResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.responseValue = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): ComplexResponse {
    return { responseValue: isSet(object.responseValue) ? String(object.responseValue) : "" };
  },

  toJSON(message: ComplexResponse): unknown {
    const obj: any = {};
    message.responseValue !== undefined && (obj.responseValue = message.responseValue);
    return obj;
  },

  create(base?: DeepPartial<ComplexResponse>): ComplexResponse {
    return ComplexResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ComplexResponse>): ComplexResponse {
    const message = createBaseComplexResponse();
    message.responseValue = object.responseValue ?? "";
    return message;
  },
};

function createBaseTimeoutResponse(): TimeoutResponse {
  return { timeoutOccurred: false };
}

export const TimeoutResponse = {
  encode(message: TimeoutResponse, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.timeoutOccurred === true) {
      writer.uint32(8).bool(message.timeoutOccurred);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): TimeoutResponse {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseTimeoutResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 8) {
            break;
          }

          message.timeoutOccurred = reader.bool();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): TimeoutResponse {
    return { timeoutOccurred: isSet(object.timeoutOccurred) ? Boolean(object.timeoutOccurred) : false };
  },

  toJSON(message: TimeoutResponse): unknown {
    const obj: any = {};
    message.timeoutOccurred !== undefined && (obj.timeoutOccurred = message.timeoutOccurred);
    return obj;
  },

  create(base?: DeepPartial<TimeoutResponse>): TimeoutResponse {
    return TimeoutResponse.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<TimeoutResponse>): TimeoutResponse {
    const message = createBaseTimeoutResponse();
    message.timeoutOccurred = object.timeoutOccurred ?? false;
    return message;
  },
};

function createBaseInvokeSequentiallyRequest(): InvokeSequentiallyRequest {
  return { executeAsBackgroundCall: [] };
}

export const InvokeSequentiallyRequest = {
  encode(message: InvokeSequentiallyRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    writer.uint32(10).fork();
    for (const v of message.executeAsBackgroundCall) {
      writer.bool(v);
    }
    writer.ldelim();
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): InvokeSequentiallyRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseInvokeSequentiallyRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag == 8) {
            message.executeAsBackgroundCall.push(reader.bool());
            continue;
          }

          if (tag == 10) {
            const end2 = reader.uint32() + reader.pos;
            while (reader.pos < end2) {
              message.executeAsBackgroundCall.push(reader.bool());
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

  fromJSON(object: any): InvokeSequentiallyRequest {
    return {
      executeAsBackgroundCall: Array.isArray(object?.executeAsBackgroundCall)
        ? object.executeAsBackgroundCall.map((e: any) => Boolean(e))
        : [],
    };
  },

  toJSON(message: InvokeSequentiallyRequest): unknown {
    const obj: any = {};
    if (message.executeAsBackgroundCall) {
      obj.executeAsBackgroundCall = message.executeAsBackgroundCall.map((e) => e);
    } else {
      obj.executeAsBackgroundCall = [];
    }
    return obj;
  },

  create(base?: DeepPartial<InvokeSequentiallyRequest>): InvokeSequentiallyRequest {
    return InvokeSequentiallyRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<InvokeSequentiallyRequest>): InvokeSequentiallyRequest {
    const message = createBaseInvokeSequentiallyRequest();
    message.executeAsBackgroundCall = object.executeAsBackgroundCall?.map((e) => e) || [];
    return message;
  },
};

export interface Coordinator {
  sleep(request: Duration): Promise<Empty>;
  proxy(request: Empty): Promise<ProxyResponse>;
  complex(request: ComplexRequest): Promise<ComplexResponse>;
  timeout(request: Duration): Promise<TimeoutResponse>;
  invokeSequentially(request: InvokeSequentiallyRequest): Promise<Empty>;
}

export class CoordinatorClientImpl implements Coordinator {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "coordinator.Coordinator";
    this.rpc = rpc;
    this.sleep = this.sleep.bind(this);
    this.proxy = this.proxy.bind(this);
    this.complex = this.complex.bind(this);
    this.timeout = this.timeout.bind(this);
    this.invokeSequentially = this.invokeSequentially.bind(this);
  }
  sleep(request: Duration): Promise<Empty> {
    const data = Duration.encode(request).finish();
    const promise = this.rpc.request(this.service, "Sleep", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  proxy(request: Empty): Promise<ProxyResponse> {
    const data = Empty.encode(request).finish();
    const promise = this.rpc.request(this.service, "Proxy", data);
    return promise.then((data) => ProxyResponse.decode(_m0.Reader.create(data)));
  }

  complex(request: ComplexRequest): Promise<ComplexResponse> {
    const data = ComplexRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Complex", data);
    return promise.then((data) => ComplexResponse.decode(_m0.Reader.create(data)));
  }

  timeout(request: Duration): Promise<TimeoutResponse> {
    const data = Duration.encode(request).finish();
    const promise = this.rpc.request(this.service, "Timeout", data);
    return promise.then((data) => TimeoutResponse.decode(_m0.Reader.create(data)));
  }

  invokeSequentially(request: InvokeSequentiallyRequest): Promise<Empty> {
    const data = InvokeSequentiallyRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "InvokeSequentially", data);
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
    "name": "coordinator.proto",
    "package": "coordinator",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "Duration",
      "field": [{
        "name": "millis",
        "number": 1,
        "label": 1,
        "type": 4,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "millis",
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
      "name": "ProxyResponse",
      "field": [{
        "name": "message",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "message",
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
      "name": "ComplexRequest",
      "field": [{
        "name": "sleep_duration",
        "number": 1,
        "label": 1,
        "type": 11,
        "typeName": ".coordinator.Duration",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "sleepDuration",
        "options": undefined,
        "proto3Optional": false,
      }, {
        "name": "request_value",
        "number": 2,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "requestValue",
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
      "name": "ComplexResponse",
      "field": [{
        "name": "response_value",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "responseValue",
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
      "name": "TimeoutResponse",
      "field": [{
        "name": "timeout_occurred",
        "number": 1,
        "label": 1,
        "type": 8,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "timeoutOccurred",
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
      "name": "InvokeSequentiallyRequest",
      "field": [{
        "name": "execute_as_background_call",
        "number": 1,
        "label": 3,
        "type": 8,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "executeAsBackgroundCall",
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
      "name": "Coordinator",
      "method": [{
        "name": "Sleep",
        "inputType": ".coordinator.Duration",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Proxy",
        "inputType": ".google.protobuf.Empty",
        "outputType": ".coordinator.ProxyResponse",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Complex",
        "inputType": ".coordinator.ComplexRequest",
        "outputType": ".coordinator.ComplexResponse",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Timeout",
        "inputType": ".coordinator.Duration",
        "outputType": ".coordinator.TimeoutResponse",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "InvokeSequentially",
        "inputType": ".coordinator.InvokeSequentiallyRequest",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.coordinator",
      "javaOuterClassname": "CoordinatorProto",
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
      "csharpNamespace": "Coordinator",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Coordinator",
      "phpMetadataNamespace": "Coordinator\\GPBMetadata",
      "rubyPackage": "Coordinator",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".coordinator.Duration": Duration,
    ".coordinator.ProxyResponse": ProxyResponse,
    ".coordinator.ComplexRequest": ComplexRequest,
    ".coordinator.ComplexResponse": ComplexResponse,
    ".coordinator.TimeoutResponse": TimeoutResponse,
    ".coordinator.InvokeSequentiallyRequest": InvokeSequentiallyRequest,
    ".coordinator.Coordinator": CoordinatorClientImpl,
  },
  dependencies: [protoMetadata1, protoMetadata2],
  options: { services: { "Coordinator": { options: { "service_type": 0 }, methods: {} } } },
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
