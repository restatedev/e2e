package interpreter

import (
	"crypto/sha256"
	"encoding/binary"
)

type Random struct {
	randstate256 [4]uint64
}

func NewRandom(seed string) *Random {
	hash := sha256.Sum256([]byte(seed))
	return &Random{
		randstate256: [4]uint64{
			binary.LittleEndian.Uint64(hash[0:8]),
			binary.LittleEndian.Uint64(hash[8:16]),
			binary.LittleEndian.Uint64(hash[16:24]),
			binary.LittleEndian.Uint64(hash[24:32]),
		},
	}
}

const (
	U64_MASK = (1 << 64) - 1
	U53_MASK = (1 << 53) - 1
)

// xoshiro256++
// https://prng.di.unimi.it/xoshiro256plusplus.c - public domain
func (r *Random) U64() uint64 {
	result := (rotl((r.randstate256[0]+r.randstate256[3])&U64_MASK, 23) +
		r.randstate256[0]) & U64_MASK

	t := (r.randstate256[1] << 17) & U64_MASK

	r.randstate256[2] ^= r.randstate256[0]
	r.randstate256[3] ^= r.randstate256[1]
	r.randstate256[1] ^= r.randstate256[2]
	r.randstate256[0] ^= r.randstate256[3]

	r.randstate256[2] ^= t

	r.randstate256[3] = rotl(r.randstate256[3], 45)

	return result
}

func (r *Random) Random() float64 {
	u53 := r.U64() & U53_MASK
	return float64(u53) / float64(1<<53)
}

func rotl(x uint64, k uint) uint64 {
	return ((x << k) & U64_MASK) | (x >> (64 - k))
}

type WeightedRandom[T comparable] struct {
	items []T
	cdf   []float64
}

func NewWeightedRandom[T comparable](items []T, ranks []int) *WeightedRandom[T] {
	sum := 0.0
	for _, n := range ranks {
		sum += float64(n)
	}

	pdf := make([]float64, len(ranks))
	for i, n := range ranks {
		pdf[i] = float64(n) / sum
	}

	cdf := make([]float64, len(ranks))
	cdf[0] = pdf[0]
	for i := 1; i < len(ranks); i++ {
		cdf[i] = cdf[i-1] + pdf[i]
	}

	return &WeightedRandom[T]{
		items: items,
		cdf:   cdf,
	}
}

func NewWeightedRandomFromMap[K comparable](items map[K]int) *WeightedRandom[K] {
	keys := make([]K, 0, len(items))
	ranks := make([]int, 0, len(items))
	for k, v := range items {
		keys = append(keys, k)
		ranks = append(ranks, v)
	}
	return NewWeightedRandom(keys, ranks)
}

func (wr *WeightedRandom[T]) Next(random *Random) T {
	w := random.Random()
	for i, v := range wr.cdf {
		if w <= v {
			return wr.items[i]
		}
	}
	return wr.items[len(wr.items)-1]
}
