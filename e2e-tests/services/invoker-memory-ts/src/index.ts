import * as restate from "@restatedev/restate-sdk";

const ALPHANUM =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

function randomString(length: number): string {
  let s = "";
  for (let i = 0; i < length; i++) {
    s += ALPHANUM[Math.floor(Math.random() * ALPHANUM.length)];
  }
  return s;
}

const KB = 1024;

const memoryPressureService = restate.service({
  name: "MemoryPressureService",
  handlers: {
    generate: async (
      ctx: restate.Context,
      input: string,
    ): Promise<string> => {
      for (let i = 0; i < 10; i++) {
        await ctx.run<string>(() => randomString(64 * KB));
      }
      return `ok-${input}`;
    },
    generateOversized: async (
      ctx: restate.Context,
      input: string,
    ): Promise<string> => {
      await ctx.run<string>(() => randomString(512 * KB));
      return `ok-${input}`;
    },
  },
  options: { journalRetention: { seconds: 0 } },
});

const statefulObject = restate.object({
  name: "StatefulObject",
  handlers: {
    initState: async (
      ctx: restate.ObjectContext,
      _input: string,
    ): Promise<void> => {
      ctx.set("state-a", randomString(32 * KB));
      ctx.set("state-b", randomString(32 * KB));
    },
    readState: async (
      ctx: restate.ObjectContext,
      _input: string,
    ): Promise<number> => {
      const a = (await ctx.get<string>("state-a")) ?? "";
      const b = (await ctx.get<string>("state-b")) ?? "";
      return a.length + b.length;
    },
    readLargeState: async (
      ctx: restate.ObjectContext,
      _input: string,
    ): Promise<number> => {
      const data = (await ctx.get<string>("large-state")) ?? "";
      return data.length;
    },
  },
  options: { journalRetention: { seconds: 0 } },
});

// Port resolution: defaults to the PORT env var, falling back to 9080. The
// ServiceDeploymentContainer in `infra/` already injects PORT=9080, matching
// ServiceSpec.toHostnamePort.
restate.serve({ services: [memoryPressureService, statefulObject] });
