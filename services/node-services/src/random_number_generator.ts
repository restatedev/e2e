import * as restate from "@restatedev/restate-sdk";

import {
  RandomNumberListGenerator as IRandomNumberListGeneratorService,
  protobufPackage, GenerateNumbersRequest, GenerateNumbersResponse
} from "./generated/rng";
import { NumberSortHttpServerUtils } from "./number_sort_utils";

export const RandomNumberListGeneratorServiceFQN = protobufPackage + ".RandomNumberListGeneratorService";

export class RandomNumberListGeneratorService implements IRandomNumberListGeneratorService {

  async generateNumbers(request: GenerateNumbersRequest): Promise<GenerateNumbersResponse> {
    const ctx = restate.useContext(this);

    const numbers = [...Array(request.itemsNumber)].map(e=> Math.random()*request.itemsNumber)

    const awakeable = ctx.awakeable<number[]>();

    await ctx.sideEffect(async () => {
      NumberSortHttpServerUtils.sendSortNumbersRequest(awakeable.id, numbers);
    })

    const sortedNumbers: number[] = await awakeable.promise;

    return GenerateNumbersResponse.create({numbers: sortedNumbers});
  }
}
