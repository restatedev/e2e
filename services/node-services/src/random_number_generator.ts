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
