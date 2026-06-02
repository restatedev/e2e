# syntax=docker/dockerfile:1.7
# Copyright (c) 2023 - 2026 Restate Software, Inc., Restate GmbH
#
# This file is part of the Restate e2e test suite,
# which is released under the MIT license.
#
# You can find a copy of the license in file LICENSE in the root
# directory of this repository or package, or at
# https://github.com/restatedev/e2e/blob/main/LICENSE
#
# No `--platform=$BUILDPLATFORM` pin on the builder: we *want* `docker buildx`
# to run the builder stage at the target architecture (under QEMU when it
# differs from the host) so `cargo build --release` produces a binary matching
# `$TARGETPLATFORM`. Pinning the builder to the host would compile for the
# host's triple and copy that host-arch binary into every per-arch runtime
# image, yielding `exec format error` on the foreign arch.
#
# Emulated Rust builds are slow (~5-10× native); acceptable for a manually
# triggered publish. Cross-compile via `rustup target add` + a `--target`
# flag if/when that becomes painful.
FROM rust:1.90-slim AS builder

WORKDIR /app

COPY rust-toolchain.toml Cargo.toml ./
COPY Cargo.lock* ./
COPY src ./src

# BuildKit cache mounts speed up incremental rebuilds; they're keyed per-arch
# so each target platform has its own registry / target dir.
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/app/target,sharing=locked \
    cargo build --release && \
    cp target/release/e2e-test-services /usr/local/bin/e2e-test-services

FROM debian:bookworm-slim

# `restate-sdk` uses rustls + ring/aws-lc-rs — no system OpenSSL needed.
# ca-certificates is included for any outbound HTTPS the service might add later.
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /usr/local/bin/e2e-test-services /usr/local/bin/e2e-test-services

EXPOSE 9080
ENV RUST_LOG=info
CMD ["/usr/local/bin/e2e-test-services"]
