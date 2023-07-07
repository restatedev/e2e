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
    const ctx = restate.useContext(this);

    const list = (await ctx.get<List>(LIST_KEY)) ?? List.create({});
    list.values.push(request.value);
    ctx.set(LIST_KEY, list);

    return Empty.create({});
  }

  async clear(request: Request): Promise<List> {
    console.log("clear: " + JSON.stringify(request));
    const ctx = restate.useContext(this);

    const list = (await ctx.get<List>(LIST_KEY)) ?? List.create({});
    ctx.clear(LIST_KEY);

    return list;
  }

  get(): Promise<List> {
    throw new Error("Method not implemented.");
  }
}
