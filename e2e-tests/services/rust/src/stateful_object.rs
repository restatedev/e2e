// Copyright (c) 2023 - 2026 Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e test suite,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

//! `StatefulObject`, consumed by `InvokerMemoryTest` in the JVM e2e suite.
//!
//! The Kotlin test holds a matching `@VirtualObject` interface at
//! `e2e-tests/src/main/kotlin/dev/restate/sdktesting/contracts/StatefulObject.kt`;
//! the trait and handler names below must stay in sync with that contract.

use crate::util::{KB, random_string};
use restate_sdk::prelude::*;

#[restate_sdk::object]
#[name = "StatefulObject"]
pub trait StatefulObject {
    #[name = "initState"]
    async fn init_state(input: String) -> Result<(), HandlerError>;
    #[name = "readState"]
    async fn read_state(input: String) -> Result<i32, HandlerError>;
    #[name = "readLargeState"]
    async fn read_large_state(input: String) -> Result<i32, HandlerError>;
}

pub struct StatefulObjectImpl;

impl StatefulObject for StatefulObjectImpl {
    async fn init_state(
        &self,
        ctx: ObjectContext<'_>,
        _input: String,
    ) -> Result<(), HandlerError> {
        // 2 × 32 KiB → 64 KiB total state per virtual object.
        ctx.set("state-a", random_string(32 * KB));
        ctx.set("state-b", random_string(32 * KB));
        Ok(())
    }

    async fn read_state(
        &self,
        ctx: ObjectContext<'_>,
        _input: String,
    ) -> Result<i32, HandlerError> {
        let a: String = ctx.get("state-a").await?.unwrap_or_default();
        let b: String = ctx.get("state-b").await?.unwrap_or_default();
        Ok((a.len() + b.len()) as i32)
    }

    async fn read_large_state(
        &self,
        ctx: ObjectContext<'_>,
        _input: String,
    ) -> Result<i32, HandlerError> {
        let data: String = ctx.get("large-state").await?.unwrap_or_default();
        Ok(data.len() as i32)
    }
}
