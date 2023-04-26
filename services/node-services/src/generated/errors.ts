/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "errors";

export interface FailRequest {
  key: string;
}

export interface ErrorMessage {
  key: string;
  errorMessage: string;
}

function createBaseFailRequest(): FailRequest {
  return { key: "" };
}

export const FailRequest = {
  encode(message: FailRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== "") {
      writer.uint32(10).string(message.key);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): FailRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseFailRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.key = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): FailRequest {
    return { key: isSet(object.key) ? String(object.key) : "" };
  },

  toJSON(message: FailRequest): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = message.key);
    return obj;
  },

  create(base?: DeepPartial<FailRequest>): FailRequest {
    return FailRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<FailRequest>): FailRequest {
    const message = createBaseFailRequest();
    message.key = object.key ?? "";
    return message;
  },
};

function createBaseErrorMessage(): ErrorMessage {
  return { key: "", errorMessage: "" };
}

export const ErrorMessage = {
  encode(message: ErrorMessage, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.key !== "") {
      writer.uint32(10).string(message.key);
    }
    if (message.errorMessage !== "") {
      writer.uint32(18).string(message.errorMessage);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): ErrorMessage {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseErrorMessage();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.key = reader.string();
          continue;
        case 2:
          if (tag != 18) {
            break;
          }

          message.errorMessage = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): ErrorMessage {
    return {
      key: isSet(object.key) ? String(object.key) : "",
      errorMessage: isSet(object.errorMessage) ? String(object.errorMessage) : "",
    };
  },

  toJSON(message: ErrorMessage): unknown {
    const obj: any = {};
    message.key !== undefined && (obj.key = message.key);
    message.errorMessage !== undefined && (obj.errorMessage = message.errorMessage);
    return obj;
  },

  create(base?: DeepPartial<ErrorMessage>): ErrorMessage {
    return ErrorMessage.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<ErrorMessage>): ErrorMessage {
    const message = createBaseErrorMessage();
    message.key = object.key ?? "";
    message.errorMessage = object.errorMessage ?? "";
    return message;
  },
};

export interface FailingService {
  fail(request: ErrorMessage): Promise<Empty>;
  failAndHandle(request: ErrorMessage): Promise<ErrorMessage>;
  invokeExternalAndHandleFailure(request: FailRequest): Promise<ErrorMessage>;
  handleNotFound(request: FailRequest): Promise<ErrorMessage>;
}

export class FailingServiceClientImpl implements FailingService {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "errors.FailingService";
    this.rpc = rpc;
    this.fail = this.fail.bind(this);
    this.failAndHandle = this.failAndHandle.bind(this);
    this.invokeExternalAndHandleFailure = this.invokeExternalAndHandleFailure.bind(this);
    this.handleNotFound = this.handleNotFound.bind(this);
  }
  fail(request: ErrorMessage): Promise<Empty> {
    const data = ErrorMessage.encode(request).finish();
    const promise = this.rpc.request(this.service, "Fail", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  failAndHandle(request: ErrorMessage): Promise<ErrorMessage> {
    const data = ErrorMessage.encode(request).finish();
    const promise = this.rpc.request(this.service, "FailAndHandle", data);
    return promise.then((data) => ErrorMessage.decode(_m0.Reader.create(data)));
  }

  invokeExternalAndHandleFailure(request: FailRequest): Promise<ErrorMessage> {
    const data = FailRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "InvokeExternalAndHandleFailure", data);
    return promise.then((data) => ErrorMessage.decode(_m0.Reader.create(data)));
  }

  handleNotFound(request: FailRequest): Promise<ErrorMessage> {
    const data = FailRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "HandleNotFound", data);
    return promise.then((data) => ErrorMessage.decode(_m0.Reader.create(data)));
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
    "name": "errors.proto",
    "package": "errors",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "FailRequest",
      "field": [{
        "name": "key",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "key",
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
      "name": "ErrorMessage",
      "field": [{
        "name": "key",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "key",
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
        "name": "error_message",
        "number": 2,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "errorMessage",
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
      "name": "FailingService",
      "method": [{
        "name": "Fail",
        "inputType": ".errors.ErrorMessage",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "FailAndHandle",
        "inputType": ".errors.ErrorMessage",
        "outputType": ".errors.ErrorMessage",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "InvokeExternalAndHandleFailure",
        "inputType": ".errors.FailRequest",
        "outputType": ".errors.ErrorMessage",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "HandleNotFound",
        "inputType": ".errors.FailRequest",
        "outputType": ".errors.ErrorMessage",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.errors",
      "javaOuterClassname": "ErrorsProto",
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
      "objcClassPrefix": "EXX",
      "csharpNamespace": "Errors",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Errors",
      "phpMetadataNamespace": "Errors\\GPBMetadata",
      "rubyPackage": "Errors",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".errors.FailRequest": FailRequest,
    ".errors.ErrorMessage": ErrorMessage,
    ".errors.FailingService": FailingServiceClientImpl,
  },
  dependencies: [protoMetadata1, protoMetadata2],
  options: {
    messages: {
      "FailRequest": { fields: { "key": { "field": 0 } } },
      "ErrorMessage": { fields: { "key": { "field": 0 } } },
    },
    services: { "FailingService": { options: { "service_type": 1 }, methods: {} } },
  },
};

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends Array<infer U> ? Array<DeepPartial<U>> : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}
