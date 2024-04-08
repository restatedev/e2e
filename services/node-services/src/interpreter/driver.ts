// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import * as http from "node:http";
import { Test, TestConfiguration, TestStatus } from "./test_driver";

let TEST: Test | undefined = undefined;

/**
 * Start a runner as a batch job
 */
export const createJob = async () => {
  const conf = readJsonEnv();
  TEST = new Test(conf);
  return TEST.go();
};

/**
 * Start a webserver
 */
export const createServer = (port: number) => {
  http
    .createServer(async function (req, res) {
      const url = req.url ?? "/";
      if (url === "/start" && req.method === "POST") {
        const conf = await readJson<TestConfiguration>(req);
        const status = TEST?.testStatus() ?? TestStatus.NOT_STARTED;

        if (status !== TestStatus.NOT_STARTED) {
          writeJsonResponse(400, { status }, res);
          return;
        }

        TEST = new Test(conf);
        TEST.go().catch((e) => console.log(e));

        writeJsonResponse(200, { status: TEST.testStatus() }, res);
      } else if (url === "/status") {
        writeJsonResponse(
          200,
          { status: TEST?.testStatus() ?? TestStatus.NOT_STARTED },
          res
        );
      } else if (url === "/sample") {
        const conf = await readJson<TestConfiguration>(req);
        const test = new Test(conf);
        const generator = test.generate();
        const itemSafe = generator.next();
        if (!itemSafe.done) {
          const { program } = itemSafe.value;
          writeJsonResponse(200, program, res);
        } else {
          writeJsonResponse(
            400,
            { cause: "please specify at least one test" },
            res
          );
        }
      } else {
        writeJsonResponse(404, {}, res);
      }
    })
    .listen(port);
};

async function readJson<T>(req: http.IncomingMessage): Promise<T> {
  let data = "";
  for await (const chunk of req) {
    data += chunk;
  }
  return JSON.parse(data) as T;
}

function writeJsonResponse(
  code: number,
  body: unknown,
  res: http.ServerResponse<http.IncomingMessage>
) {
  res.writeHead(code, { "Content-Type": "application/json" });
  res.write(JSON.stringify(body));
  res.end();
}

function readJsonEnv(): TestConfiguration {
  const conf = process.env.INTERPRETER_DRIVER_CONF;
  if (!conf) {
    throw new Error(`Missing INTERPRETER_DRIVER_CONF env var`);
  }
  return JSON.parse(conf) as TestConfiguration;
}
