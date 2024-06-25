// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import http from "node:http";
import { Buffer } from "node:buffer";

/* eslint-disable @typescript-eslint/no-explicit-any */

export default function h1server(handler: {
  fetch: (request: Request) => Promise<Response>;
}) {
  let port = 9080;
  if (process.env.PORT) {
    try {
      port = parseInt(process.env.PORT);
    } catch (e) {
      console.log(`Failed parsing ${process.env.PORT} with: `, e);
      throw e;
    }
  }
  const nodeHandler = nodeHandlerFromFetchHandler(handler.fetch);
  http
    .createServer(
      {
        keepAlive: true,
      },
      nodeHandler
    )
    .listen({ port, host: "0.0.0.0", backlog: 1024 * 1024 });
}

function nodeHandlerFromFetchHandler(
  handler: (request: Request) => Promise<Response>
): (req: http.IncomingMessage, res: http.ServerResponse) => void {
  return (r, res) => {
    toWeb(r)
      .then((request) => handler(request))
      .then(async (response: any) => {
        const body = await response.arrayBuffer();
        const headers = Object.fromEntries(response.headers.entries());
        res.writeHead(response.status, headers);
        res.end(Buffer.from(body));
      });
  };
}

function toWeb(req: http.IncomingMessage): Promise<Request> {
  const { headers, method, url } = req;

  return new Promise((resolve, reject) => {
    const body: Uint8Array[] = [];

    req.on("data", (chunk: Uint8Array) => {
      body.push(chunk);
    });

    req.on("end", () => {
      const buf = Buffer.concat(body);

      const h = new Headers();
      for (const [k, v] of Object.entries(headers)) {
        // FIXME v can be something other than string.
        h.append(k, v as string);
      }

      // Create a new Request object
      const fullURL = new URL(`http://localhost${url}`);
      const request = new Request(fullURL, {
        method,
        headers: h,
        body: method !== "GET" && method !== "HEAD" ? buf : undefined,
      });

      resolve(request);
    });

    req.on("error", (err) => {
      reject(err);
    });
  });
}
