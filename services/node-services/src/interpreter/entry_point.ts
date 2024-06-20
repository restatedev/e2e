// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import { REGISTRY } from "../services";
import { createInterpreterObject } from "./interpreter";
import { serviceInterpreterHelper } from "./services";
import { createServer, createJob } from "./driver";

/**
 * Hardcode for now, the Max number of InterpreterObject layers.
 * Each layer is represented by a VirtualObject that implements the ObjectInterpreter interface,
 * And named: `ObjectInterpreterL${layer}.
 *
 * i.e. (for 3 layers we get):
 *
 * ObjectInterpreterL0, ObjectInterpreterL1,ObjectInterpreterL2
 *
 * Each ObjectInterpreter is only allowed to preform blocking calls to the next layer,
 * to avoid deadlocks.
 *
 */

REGISTRY.addService(serviceInterpreterHelper);
REGISTRY.addObject(createInterpreterObject(0));
REGISTRY.addObject(createInterpreterObject(1));
REGISTRY.addObject(createInterpreterObject(2));

REGISTRY.add({
  fqdn: "InterpreterDriver",
  binder: () => {
    const port = process.env.INTERPRETER_DRIVER_PORT ?? "3000";
    createServer(parseInt(port));
  },
});

REGISTRY.add({
  fqdn: "InterpreterDriverJob",
  binder: () => {
    let done = false;

    createJob()
      .then((status) => {
        console.log(`Job success! ${status}`);
        process.exit(0);
      })
      .catch((e) => {
        console.log(`Job failure ${e}`);
        process.exit(1);
      })
      .finally(() => {
        done = true;
      });

    (function wait() {
      // prevent node from exiting
      if (!done) setTimeout(wait, 1000);
    })();
  },
});
