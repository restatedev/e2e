import { RestateEndpoint } from "@restatedev/restate-sdk";

export type IComponent = {
  fqdn: string;
  binder: (endpoint: RestateEndpoint) => void;
};

export class ComponenetRegistery {
  constructor(readonly components: Map<string, IComponent> = new Map()) {}

  add(c: IComponent) {
    this.components.set(c.fqdn, c);
  }

  register(fqdns: Set<string>, e: RestateEndpoint) {
    fqdns.forEach((fqdn) => {
      const c = this.components.get(fqdn);
      if (!c) {
        throw new Error(`unknown fqdn ${fqdn}`);
      }
      c.binder(e);
    });
  }
}

export const REGISTRY = new ComponenetRegistery();
