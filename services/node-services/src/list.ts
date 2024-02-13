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
  AppendRequest,
  ListService as IListService,
  List,
  Request,
  protobufPackage,
} from "./generated/list";
import { Empty } from "./generated/google/protobuf/empty";

const LIST_KEY = "list";

export const ListServiceFQN = protobufPackage + ".ListService";

export class ListService implements IListService {
  async append(request: AppendRequest): Promise<Empty> {
    console.log("append: " + JSON.stringify(request));
    const ctx = restate.useKeyedContext(this);

    const list = (await ctx.get<List>(LIST_KEY)) ?? List.create({});
    list.values.push(request.value);
    ctx.set(LIST_KEY, list);

    return Empty.create({});
  }

  async clear(request: Request): Promise<List> {
    console.log("clear: " + JSON.stringify(request));
    const ctx = restate.useKeyedContext(this);

    const list = (await ctx.get<List>(LIST_KEY)) ?? List.create({});
    ctx.clear(LIST_KEY);

    return list;
  }

  async get(request: Request): Promise<List> {
    console.log("get: " + JSON.stringify(request));
    const ctx = restate.useKeyedContext(this);

    return (await ctx.get<List>(LIST_KEY)) ?? List.create({});
  }
}
