// Copyright (c) 2023 - 2026 Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e test suite,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

//! Aggregate Rust binary hosting all Rust-side service implementations consumed
//! by the JVM e2e test suite.
//!
//! The binary reads a comma-separated list of service names from the `SERVICES`
//! environment variable (the same variable the Testcontainers-based deployer in
//! `infra/.../ServiceSpec.kt` injects for sibling service containers). Each
//! requested service is bound to a Restate HTTP/2 endpoint listening on `$PORT`
//! (default `9080`). If `SERVICES` is missing or empty all known services are
//! bound — a convenient default for local development.
//!
//! To add a new service:
//!   1. Create a new module under `src/` with the trait, impl struct and
//!      `#[restate_sdk::service]` / `#[restate_sdk::object]` annotation.
//!   2. Declare the module below (`mod my_service;`).
//!   3. Add a match arm in `bind_services` and an entry in `ALL_SERVICES`.
//!   4. Add a Kotlin contract interface in
//!      `e2e-tests/src/main/kotlin/dev/restate/sdktesting/contracts/`.

mod memory_pressure_service;
mod stateful_object;
mod util;

use crate::memory_pressure_service::{MemoryPressureService, MemoryPressureServiceImpl};
use crate::stateful_object::{StatefulObject, StatefulObjectImpl};
use restate_sdk::endpoint::Builder;
use restate_sdk::prelude::*;
use std::time::Duration;

/// Names of every service this binary can bind. Used as the default set when
/// `SERVICES` is not set.
const ALL_SERVICES: &[&str] = &["MemoryPressureService", "StatefulObject"];

/// Apply the given service names to the endpoint builder. Unknown names are
/// logged and skipped — at worst the ingress returns 404 on the first call,
/// which is louder and easier to diagnose than a startup crash.
fn bind_services(mut b: Builder, names: &[&str]) -> Builder {
    // journal_retention = 0 mirrors the in-process Kotlin path's
    // `it.journalRetention = 0.seconds`. InvokerMemoryTest creates large payloads
    // in journal steps and queries sys_journal at teardown, so retaining them
    // would bloat the table. Override per-arm if a future service needs more.
    let opts = || ServiceOptions::default().journal_retention(Duration::ZERO);
    for name in names {
        b = match *name {
            "MemoryPressureService" => {
                b.bind_with_options(MemoryPressureServiceImpl.serve(), opts())
            }
            "StatefulObject" => b.bind_with_options(StatefulObjectImpl.serve(), opts()),
            unknown => {
                tracing::warn!(service = unknown, "Unknown service name in SERVICES; skipping");
                b
            }
        };
    }
    b
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .init();

    let services_var = std::env::var("SERVICES").unwrap_or_default();
    let requested: Vec<&str> = if services_var.trim().is_empty() {
        ALL_SERVICES.to_vec()
    } else {
        services_var
            .split(',')
            .map(|s| s.trim())
            .filter(|s| !s.is_empty())
            .collect()
    };
    tracing::info!(?requested, "Binding services");

    let endpoint = bind_services(Endpoint::builder(), &requested).build();

    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(9080);

    HttpServer::new(endpoint)
        .listen_and_serve(([0, 0, 0, 0], port).into())
        .await;
}
