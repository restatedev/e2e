// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

/// copied from our sdk-typescript
//! Some parts copied from https://github.com/uuidjs/uuid/blob/main/src/stringify.js
//! License MIT

import { createHash } from "node:crypto";

export class Random {
  private randstate256: [bigint, bigint, bigint, bigint];

  constructor(seed: string) {
    const hash = createHash("sha256").update(seed).digest();

    this.randstate256 = [
      hash.readBigUInt64LE(0),
      hash.readBigUInt64LE(8),
      hash.readBigUInt64LE(16),
      hash.readBigUInt64LE(24),
    ];
  }

  static U64_MASK = (1n << 64n) - 1n;
  static U53_MASK = (1n << 53n) - 1n;

  // xoshiro256++
  // https://prng.di.unimi.it/xoshiro256plusplus.c - public domain
  public u64(): bigint {
    const result: bigint =
      (Random.rotl(
        (this.randstate256[0] + this.randstate256[3]) & Random.U64_MASK,
        23n
      ) +
        this.randstate256[0]) &
      Random.U64_MASK;

    const t: bigint = (this.randstate256[1] << 17n) & Random.U64_MASK;

    this.randstate256[2] ^= this.randstate256[0];
    this.randstate256[3] ^= this.randstate256[1];
    this.randstate256[1] ^= this.randstate256[2];
    this.randstate256[0] ^= this.randstate256[3];

    this.randstate256[2] ^= t;

    this.randstate256[3] = Random.rotl(this.randstate256[3], 45n);

    return result;
  }

  public random(): number {
    // first generate a uint in range [0,2^53), which can be mapped 1:1 to a float64 in [0,1)
    const u53 = this.u64() & Random.U53_MASK;
    // then divide by 2^53, which will simply update the exponent
    return Number(u53) / 2 ** 53;
  }

  static rotl(x: bigint, k: bigint): bigint {
    return ((x << k) & Random.U64_MASK) | (x >> (64n - k));
  }
}

export class WeightedRandom<T> {
  private readonly cdf: number[];

  static from<K extends number | string | symbol>(
    items: Record<K, number>
  ): WeightedRandom<K> {
    return new WeightedRandom<K>(
      Object.keys(items) as K[],
      Object.values(items)
    );
  }

  constructor(private readonly items: T[], ranks: number[]) {
    // compute PDF
    let sum = 0;
    for (const n of ranks) {
      sum += n;
    }
    const pdf = ranks.map((n) => n / sum);
    // compute CDF
    const cdf = [pdf[0]];
    for (let i = 1; i < ranks.length; i++) {
      cdf[i] = cdf[i - 1] + pdf[i];
    }
    this.cdf = cdf;
  }

  next(random: Random): T {
    const w = random.random();
    const cdf = this.cdf;
    for (let i = 0; i < cdf.length; i++) {
      if (w <= cdf[i]) {
        return this.items[i];
      }
    }
    return this.items[this.items.length - 1];
  }
}
