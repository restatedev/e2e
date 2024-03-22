// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";
import * as crypto from "node:crypto";

import { REGISTRY } from "./services";
import { NumberSortHttpServerUtils } from "./number_sort_utils";

const service = restate.service({
  name: "RandomNumberListGenerator",
  handlers: {
    async generateNumbers(
      ctx: restate.Context,
      itemsToGenerate: number
    ): Promise<number[]> {
      const list: number[] = [];
      for (let i = 0; i < itemsToGenerate; i++) {
        const n = crypto.randomInt(1024 * 1024 * 1024);
        list.push(n);
      }

      const { id, promise } = ctx.awakeable<number[]>();
      await ctx.sideEffect(() =>
        NumberSortHttpServerUtils.sendSortNumbersRequest(id, list)
      );

      return promise;
    },
  },
});

REGISTRY.addService(service);
