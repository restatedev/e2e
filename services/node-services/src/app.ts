// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as restate from "@restatedev/restate-sdk";

import "./awakeable_holder";
import "./counter";
import "./coordinator";
import "./receiver";
import "./event_handler";
import "./list";
import "./map";
import "./proxy_counter";
import "./cancel_test";
import "./non_determinism";
import "./random_number_list";
import "./failing";
import "./side_effect";

import { REGISTRY } from "./services";

if (!process.env.SERVICES) {
  throw new Error("Cannot find SERVICES env");
}
const fqdns = new Set(process.env.SERVICES.split(","));
const endpoint = restate.endpoint();
REGISTRY.register(fqdns, endpoint);

if (!process.env.AWS_LAMBDA_FUNCTION_NAME) {
  endpoint.listen();
}

export const handler = endpoint.lambdaHandler();
