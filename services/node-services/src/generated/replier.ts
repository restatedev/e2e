/* eslint-disable */
import _m0 from "protobufjs/minimal";
import { FileDescriptorProto as FileDescriptorProto1 } from "ts-proto-descriptors";
import { protoMetadata as protoMetadata2 } from "./dev/restate/ext";
import { Empty, protoMetadata as protoMetadata1 } from "./google/protobuf/empty";

export const protobufPackage = "restate.e2e.externalcall.replier";

export interface Reply {
  replyIdentifier: Buffer;
  payload: Buffer;
}

function createBaseReply(): Reply {
  return { replyIdentifier: Buffer.alloc(0), payload: Buffer.alloc(0) };
}

export const Reply = {
  encode(message: Reply, writer: _m0.Writer = _m0.Writer.create()): _m0.Writer {
    if (message.replyIdentifier.length !== 0) {
      writer.uint32(10).bytes(message.replyIdentifier);
    }
    if (message.payload.length !== 0) {
      writer.uint32(18).bytes(message.payload);
    }
    return writer;
  },

  decode(input: _m0.Reader | Uint8Array, length?: number): Reply {
    const reader = input instanceof _m0.Reader ? input : _m0.Reader.create(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseReply();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1:
          if (tag != 10) {
            break;
          }

          message.replyIdentifier = reader.bytes() as Buffer;
          continue;
        case 2:
          if (tag != 18) {
            break;
          }

          message.payload = reader.bytes() as Buffer;
          continue;
      }
      if ((tag & 7) == 4 || tag == 0) {
        break;
      }
      reader.skipType(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): Reply {
    return {
      replyIdentifier: isSet(object.replyIdentifier)
        ? Buffer.from(bytesFromBase64(object.replyIdentifier))
        : Buffer.alloc(0),
      payload: isSet(object.payload) ? Buffer.from(bytesFromBase64(object.payload)) : Buffer.alloc(0),
    };
  },

  toJSON(message: Reply): unknown {
    const obj: any = {};
    message.replyIdentifier !== undefined &&
      (obj.replyIdentifier = base64FromBytes(
        message.replyIdentifier !== undefined ? message.replyIdentifier : Buffer.alloc(0),
      ));
    message.payload !== undefined &&
      (obj.payload = base64FromBytes(message.payload !== undefined ? message.payload : Buffer.alloc(0)));
    return obj;
  },

  create(base?: DeepPartial<Reply>): Reply {
    return Reply.fromPartial(base ?? {});
  },

  fromPartial(object: DeepPartial<Reply>): Reply {
    const message = createBaseReply();
    message.replyIdentifier = object.replyIdentifier ?? Buffer.alloc(0);
    message.payload = object.payload ?? Buffer.alloc(0);
    return message;
  },
};

export interface Replier {
  replyToRandomNumberListGenerator(request: Reply): Promise<Empty>;
}

export class ReplierClientImpl implements Replier {
  private readonly rpc: Rpc;
  private readonly service: string;
  constructor(rpc: Rpc, opts?: { service?: string }) {
    this.service = opts?.service || "restate.e2e.externalcall.replier.Replier";
    this.rpc = rpc;
    this.replyToRandomNumberListGenerator = this.replyToRandomNumberListGenerator.bind(this);
  }
  replyToRandomNumberListGenerator(request: Reply): Promise<Empty> {
    const data = Reply.encode(request).finish();
    const promise = this.rpc.request(this.service, "ReplyToRandomNumberListGenerator", data);
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
    "name": "replier.proto",
    "package": "restate.e2e.externalcall.replier",
    "dependency": ["google/protobuf/empty.proto", "dev/restate/ext.proto"],
    "publicDependency": [],
    "weakDependency": [],
    "messageType": [{
      "name": "Reply",
      "field": [{
        "name": "reply_identifier",
        "number": 1,
        "label": 1,
        "type": 12,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "replyIdentifier",
        "options": undefined,
        "proto3Optional": false,
      }, {
        "name": "payload",
        "number": 2,
        "label": 1,
        "type": 12,
        "typeName": "",
        "extendee": "",
        "defaultValue": "",
        "oneofIndex": 0,
        "jsonName": "payload",
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
      "name": "Replier",
      "method": [{
        "name": "ReplyToRandomNumberListGenerator",
        "inputType": ".restate.e2e.externalcall.replier.Reply",
        "outputType": ".google.protobuf.Empty",
        "options": undefined,
        "clientStreaming": false,
        "serverStreaming": false,
      }],
      "options": { "deprecated": false, "uninterpretedOption": [] },
    }],
    "extension": [],
    "options": {
      "javaPackage": "com.restate.e2e.externalcall.replier",
      "javaOuterClassname": "ReplierProto",
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
      "csharpNamespace": "Restate.E2e.Externalcall.Replier",
      "swiftPrefix": "",
      "phpClassPrefix": "",
      "phpNamespace": "Restate\\E2e\\Externalcall\\Replier",
      "phpMetadataNamespace": "Restate\\E2e\\Externalcall\\Replier\\GPBMetadata",
      "rubyPackage": "Restate::E2e::Externalcall::Replier",
      "uninterpretedOption": [],
    },
    "sourceCodeInfo": { "location": [] },
    "syntax": "proto3",
  }),
  references: {
    ".restate.e2e.externalcall.replier.Reply": Reply,
    ".restate.e2e.externalcall.replier.Replier": ReplierClientImpl,
  },
  dependencies: [protoMetadata1, protoMetadata2],
  options: { services: { "Replier": { options: { "service_type": 0 }, methods: {} } } },
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

function bytesFromBase64(b64: string): Uint8Array {
  if (tsProtoGlobalThis.Buffer) {
    return Uint8Array.from(tsProtoGlobalThis.Buffer.from(b64, "base64"));
  } else {
    const bin = tsProtoGlobalThis.atob(b64);
    const arr = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; ++i) {
      arr[i] = bin.charCodeAt(i);
    }
    return arr;
  }
}

function base64FromBytes(arr: Uint8Array): string {
  if (tsProtoGlobalThis.Buffer) {
    return tsProtoGlobalThis.Buffer.from(arr).toString("base64");
  } else {
    const bin: string[] = [];
    arr.forEach((byte) => {
      bin.push(String.fromCharCode(byte));
    });
    return tsProtoGlobalThis.btoa(bin.join(""));
  }
}

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends Array<infer U> ? Array<DeepPartial<U>> : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}
