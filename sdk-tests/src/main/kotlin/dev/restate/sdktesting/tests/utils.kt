// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter

fun VirtualObjectCommandInterpreter.InterpretRequest.Companion.getEnvVariable(env: String) =
    VirtualObjectCommandInterpreter.InterpretRequest(
        listOf(VirtualObjectCommandInterpreter.GetEnvVariable(env)))

fun VirtualObjectCommandInterpreter.InterpretRequest.Companion.awaitAwakeable(
    awakeableKey: String
) =
    VirtualObjectCommandInterpreter.InterpretRequest(
        listOf(
            VirtualObjectCommandInterpreter.AwaitOne(
                VirtualObjectCommandInterpreter.CreateAwakeable(awakeableKey))))
