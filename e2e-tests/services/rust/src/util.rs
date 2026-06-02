// Copyright (c) 2023 - 2026 Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e test suite,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

//! Helpers shared across the Rust-side service implementations.

use rand::RngExt;
use rand::distr::Alphanumeric;

pub(crate) const KB: usize = 1024;

/// Returns an alphanumeric string of the given length. The output resists
/// compression, which matters because the tests rely on each `ctx.run` block /
/// state value actually consuming the configured memory budget.
pub(crate) fn random_string(len: usize) -> String {
    let mut rng = rand::rng();
    (&mut rng)
        .sample_iter(Alphanumeric)
        .take(len)
        .map(char::from)
        .collect()
}
