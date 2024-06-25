// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

/* eslint-disable @typescript-eslint/no-explicit-any */

import * as restate from "@restatedev/restate-sdk";
import { endpoint as fetchEndpoint } from "@restatedev/restate-sdk/fetch";
import { endpoint as lambdaEndpoint } from "@restatedev/restate-sdk/lambda";
import process from "node:process";

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
import "./interpreter/entry_point";
import "./workflow";

import { REGISTRY } from "./services";
import http1Server from "./h1server";

class EndpointWrapper<K extends "fetch" | "lambda" | "node", T> {
  static fromEnvironment() {
    if (process.env.E2E_USE_FETCH) {
      return new EndpointWrapper("fetch", fetchEndpoint());
    } else if (process.env.AWS_LAMBDA_FUNCTION_NAME) {
      return new EndpointWrapper("lambda", lambdaEndpoint());
    } else {
      return new EndpointWrapper("node", restate.endpoint());
    }
  }

  constructor(readonly kind: K, readonly endpoint: T) {}
}

const wrapper = EndpointWrapper.fromEnvironment();
REGISTRY.registerFromEnvironment(wrapper.endpoint);

if (process.env.E2E_REQUEST_SIGNING) {
  const signing = process.env.E2E_REQUEST_SIGNING;
  wrapper.endpoint.withIdentityV1(...signing.split(","));
}

switch (wrapper.kind) {
  case "node": {
    wrapper.endpoint.listen();
    break;
  }
  case "fetch": {
    http1Server(wrapper.endpoint.handler());
    break;
  }
  case "lambda": {
    // do nothing, handler is exported
    break;
  }
  default:
    throw new Error("Unknown endpoint type");
}

// export for lambda. it will be only set for a lambda deployment
export const handler =
  wrapper.kind == "lambda" ? wrapper.endpoint.handler() : undefined;
