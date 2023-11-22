// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

import {
  Request,
  Result,
  ProxyService as IProxyService,
  protobufPackage,
  RetryCount,
} from "./generated/proxy";
import { Empty } from "./generated/google/protobuf/empty";

interface Rpc {
  request(
    service: string,
    method: string,
    data: Uint8Array
  ): Promise<Uint8Array>;
}

const retryCounts = new Map<string, number>();

function incrementRetryCount(req: Request) {
  retryCounts.set(JSON.stringify(req), getRetryCount(req) + 1);
}

function getRetryCount(req: Request): number {
  const count = retryCounts.get(JSON.stringify(req));
  if (count == undefined) {
    return 0;
  }
  return count;
}

export const ProxyServiceFQN = protobufPackage + ".ProxyService";

export class ProxyService implements IProxyService {
  async getRetryCount(request: Request): Promise<RetryCount> {
    return RetryCount.create({
      count: getRetryCount(request),
    });
  }

  async oneWayCall(request: Request): Promise<Empty> {
    const ctx = restate.useContext(this);
    const rpc = ctx as Rpc;

    incrementRetryCount(request);

    await ctx.oneWayCall(() =>
      rpc.request(request.serviceName, request.serviceMethod, request.message)
    );

    return {};
  }

  async call(request: Request): Promise<Result> {
    const ctx = restate.useContext(this);
    const rpc = ctx as Rpc;

    incrementRetryCount(request);

    const response = await rpc.request(
      request.serviceName,
      request.serviceMethod,
      request.message
    );

    return Result.create({ message: Buffer.from(response) });
  }
}
