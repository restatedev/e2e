/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "list";

export interface AppendRequest {
  listName: string;
  value: string;
}

export interface Request {
  listName: string;
}

export interface List {
  values: string[];
}

function createBaseAppendRequest(): AppendRequest {
  return { listName: "", value: "" };
}

export const AppendRequest = {
  encode(message: AppendRequest, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.listName !== "") {
      writer.uint32(10).string(message.listName);
    }
    if (message.value !== "") {
      writer.uint32(18).string(message.value);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): AppendRequest {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseAppendRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.listName = reader.string();
          continue;
        case 2:
          if (tag != 18) {
            break;
          }

          message.value = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): AppendRequest {
    return {
      listName: isSet(object.listName) ? String(object.listName) : "",
      value: isSet(object.value) ? String(object.value) : "",
    };
  },

  toJSON(message: AppendRequest): unknown {
    const obj: any = {};
    message.listName !== undefined && (obj.listName = message.listName);
    message.value !== undefined && (obj.value = message.value);
    return obj;
  },

  create(base?: DeepPartial<AppendRequest>): AppendRequest {
    return AppendRequest.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<AppendRequest>): AppendRequest {
    const message = createBaseAppendRequest();
    message.listName = object.listName ?? "";
    message.value = object.value ?? "";
    return message;
  },
};

function createBaseRequest(): Request {
  return { listName: "" };
}

export const Request = {
  encode(message: Request, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.listName !== "") {
      writer.uint32(10).string(message.listName);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): Request {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.listName = reader.string();
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): Request {
    return { listName: isSet(object.listName) ? String(object.listName) : "" };
  },

  toJSON(message: Request): unknown {
    const obj: any = {};
    message.listName !== undefined && (obj.listName = message.listName);
    return obj;
  },

  create(base?: DeepPartial<Request>): Request {
    return Request.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<Request>): Request {
    const message = createBaseRequest();
    message.listName = object.listName ?? "";
    return message;
  },
};

function createBaseList(): List {
  return { values: [] };
}

export const List = {
  encode(message: List, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    for (const v of message.values) {
      writer.uint32(18).string(v!);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): List {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseList();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 2:
          if (tag != 18) {
            break;
          }

          message.values.push(reader.string());
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): List {
    return { values: Array.isArray(object?.values) ? object.values.map((e: any) => String(e)) : [] };
  },

  toJSON(message: List): unknown {
    const obj: any = {};
    if (message.values) {
      obj.values = message.values.map((e) => e);
    } else {
      obj.values = [];
    }
    return obj;
  },

  create(base?: DeepPartial<List>): List {
    return List.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<List>): List {
    const message = createBaseList();
    message.values = object.values?.map((e) => e) || [];
    return message;
  },
};

export interface ListService {
  append(request: AppendRequest): Promise<Empty>;
  clear(request: Request): Promise<List>;
}

export class ListServiceClientImpl implements ListService {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "list.ListService";
    this.rpc = rpc;
    this.append = this.append.bind(this);
    this.clear = this.clear.bind(this);
  }
  append(request: AppendRequest): Promise<Empty> {
    const data = AppendRequest.encode(request).finish();
    const promise = this.rpc.request(this.service, "Append", data);
    return promise.then((data) => Empty.decode(_m0.Reader.create(data)));
  }

  clear(request: Request): Promise<List> {
    const data = Request.encode(request).finish();
    const promise = this.rpc.request(this.service, "Clear", data);
    return promise.then((data) => List.decode(_m0.Reader.create(data)));
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
    "name": "list.proto",
    "package": "list",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "AppendRequest",
      "field": [{
        "name": "list_name",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "listName",
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
        "type": 9,
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
      "name": "Request",
      "field": [{
        "name": "list_name",
        "number": 1,
        "label": 1,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "listName",
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
      "name": "List",
      "field": [{
        "name": "values",
        "number": 2,
        "label": 3,
        "type": 9,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "values",
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
      "name": "ListService",
      "method": [{
        "name": "Append",
        "inputType": ".list.AppendRequest",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }, {
        "name": "Clear",
        "inputType": ".list.Request",
        "outputType": ".list.List",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.list",
      "javaOuterClassname": "ListProto",
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
      "objcClassPrefix": "LXX",
      "csharpNamespace": "List",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "List_",
      "phpMetadataNamespace": "List_\\GPBMetadata",
      "rubyPackage": "List",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".list.AppendRequest": AppendRequest,
    ".list.Request": Request,
    ".list.List": List,
    ".list.ListService": ListServiceClientImpl,
  },
  dependencies: [protoMetadata1, protoMetadata2],
  options: {
    messages: {
      "AppendRequest": { fields: { "list_name": { "field": 0 } } },
      "Request": { fields: { "list_name": { "field": 0 } } },
    },
    services: { "ListService": { options: { "service_type": 1 }, methods: {} } },
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
