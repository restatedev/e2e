// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

import {
  ObjectContext,
  RestateEndpoint,
  Service,
  ServiceDefintion,
  VirtualObject,
  VirtualObjectDefintion,
} from "@restatedev/restate-sdk";

export type IComponent = {
  fqdn: string;
  binder: (endpoint: RestateEndpoint) => void;
};

export class ComponenetRegistery {
  constructor(readonly components: Map<string, IComponent> = new Map()) {}

  add(c: IComponent) {
    this.components.set(c.fqdn, c);
  }

  addObject(o: VirtualObjectDefintion<string, unknown>) {
    this.add({
      fqdn: o.path,
      binder: (b) => b.object(o),
    });
  }

  addService(
    s: ServiceDefintion<
      "Coordinator",
      Service<{
        sleep: (ctx: ObjectContext, request: number) => Promise<void>;
        manyTimers: (ctx: ObjectContext, request: number[]) => Promise<void>;
        proxy: (ctx: ObjectContext) => Promise<string>;
        complex: (
          ctx: ObjectContext,
          request: { sleepDurationMillis: number; requestValue: string }
        ) => Promise<string>;
        timeout: (ctx: ObjectContext, millis: number) => Promise<boolean>;
        invokeSequentially: () => never;
      }>
    >
  ) {
    this.add({
      fqdn: s.path,
      binder: (b) => b.service(s),
    });
  }

  register(fqdns: Set<string>, e: RestateEndpoint) {
    fqdns.forEach((fqdn) => {
      const c = this.components.get(fqdn);
      if (!c) {
        throw new Error(
          `unknown fqdn ${fqdn}. Did you rememeber to import the test at app.ts?`
        );
      }
      c.binder(e);
    });
  }
}

export const REGISTRY = new ComponenetRegistery();
