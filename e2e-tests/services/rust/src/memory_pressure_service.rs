// Copyright (c) 2023 - 2026 Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e test suite,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

//! `MemoryPressureService`, consumed by `InvokerMemoryTest` in the JVM e2e suite.
//!
//! The Kotlin test holds a matching `@Service` interface at
//! `e2e-tests/src/main/kotlin/dev/restate/sdktesting/contracts/MemoryPressureService.kt`;
//! the trait and handler names below must stay in sync with that contract.

use crate::util::{KB, random_string};
use restate_sdk::prelude::*;

#[restate_sdk::service]
#[name = "MemoryPressureService"]
pub trait MemoryPressureService {
    async fn generate(input: String) -> Result<String, HandlerError>;
    #[name = "generateOversized"]
    async fn generate_oversized(input: String) -> Result<String, HandlerError>;
}

pub struct MemoryPressureServiceImpl;

impl MemoryPressureService for MemoryPressureServiceImpl {
    async fn generate(&self, ctx: Context<'_>, input: String) -> Result<String, HandlerError> {
        for _ in 0..10 {
            ctx.run(|| async { Ok::<_, HandlerError>(random_string(64 * KB)) })
                .await?;
        }
        Ok(format!("ok-{input}"))
    }

    async fn generate_oversized(
        &self,
        ctx: Context<'_>,
        input: String,
    ) -> Result<String, HandlerError> {
        // 512 KiB single side effect — exceeds the 256 KiB per-invocation memory
        // limit so the runtime should pause the invocation.
        ctx.run(|| async { Ok::<_, HandlerError>(random_string(512 * KB)) })
            .await?;
        Ok(format!("ok-{input}"))
    }
}
