import express, { Request, Response } from "express";
import * as restate from "@restatedev/restate-sdk";
import { CounterAPI } from "./handler_api";

// This contains the restate ingress address in the form http://hostname:port/
const restateUri = process.env.RESTATE_URI!!;

const app = express();
app.use(express.json());

const rs = restate.connection({ ingress: restateUri });

app.post("/increment_counter_test", async (req: Request, res: Response) => {
  const { id, input } = req.body;

  const result = await rs.invoke({
    id,
    input,
    handler: async (ctx: restate.RpcContext, input: string) => {
      const { newCounter } = await ctx.rpc(CounterAPI).add(input, { value: 1 });
      return newCounter;
    },
  });

  res.json({ result });
});

export function startEmbeddedHandlerServer(port: number) {
  app.listen(port);
}
