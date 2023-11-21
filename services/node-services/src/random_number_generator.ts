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
  RandomNumberListGenerator as IRandomNumberListGeneratorService,
  protobufPackage,
  GenerateNumbersRequest,
  GenerateNumbersResponse,
} from "./generated/rng";
import { NumberSortHttpServerUtils } from "./number_sort_utils";

export const RandomNumberListGeneratorServiceFQN =
  protobufPackage + ".RandomNumberListGenerator";

export class RandomNumberListGeneratorService
  implements IRandomNumberListGeneratorService
{
  async generateNumbers(
    request: GenerateNumbersRequest
  ): Promise<GenerateNumbersResponse> {
    const ctx = restate.useContext(this);

    const numbers = Array(request.itemsNumber)
      .fill(undefined)
      .map(() => request.itemsNumber * Math.random());

    const { id, promise } = ctx.awakeable<number[]>();

    await ctx.sideEffect(async () => {
      await NumberSortHttpServerUtils.sendSortNumbersRequest(id, numbers);
    });

    const sortedNumbers: number[] = await promise;

    return GenerateNumbersResponse.create({ numbers: sortedNumbers });
  }
}
