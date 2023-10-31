import express, { Request, Response } from "express";
import * as restate from "@restatedev/restate-sdk";
import { CounterAPI } from "./handler_api";
import { NumberSortHttpServerUtils } from "./number_sort_utils";

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

app.post(
  "/one_way_increment_counter_test",
  async (req: Request, res: Response) => {
    const { id, input } = req.body;
    await rs.invoke({
      id,
      input,
      handler: async (ctx: restate.RpcContext, input: string) => {
        ctx.send(CounterAPI).add(input, { value: 1 });
        return {};
      },
    });
    res.json({});
  }
);

app.post(
  "/delayed_increment_counter_test",
  async (req: Request, res: Response) => {
    const { id, input } = req.body;
    await rs.invoke({
      id,
      input,
      handler: async (ctx: restate.RpcContext, input: string) => {
        ctx.sendDelayed(CounterAPI, 100).add(input, { value: 1 });
        return {};
      },
    });
    res.json({});
  }
);

app.post("/side_effect_and_awakeable", async (req: Request, res: Response) => {
  const { id, itemsNumber } = req.body;

  const result = await rs.invoke({
    id,
    input: itemsNumber,
    handler: async (ctx: restate.RpcContext, itemsNumber: number) => {
      const numbersToSort = Array(itemsNumber)
        .fill(undefined)
        .map(() => itemsNumber * Math.random());

      const { id, promise } = ctx.awakeable<number[]>();

      await ctx.sideEffect(async () => {
        await NumberSortHttpServerUtils.sendSortNumbersRequest(
          id,
          numbersToSort
        );
      });

      const numbers = await promise;

      return { numbers };
    },
  });

  res.json(result);
});

app.post("/consecutive_side_effects", async (req: Request, res: Response) => {
  const { id } = req.body;

  const result = await rs.invoke({
    id,
    input: {},
    handler: async (ctx: restate.RpcContext) => {
      let invocationCount = 0;
      await ctx.sideEffect<void>(async () => {
        invocationCount++;
      });
      await ctx.sideEffect<void>(async () => {
        invocationCount++;
      });
      await ctx.sideEffect<void>(async () => {
        invocationCount++;
      });

      return { invocationCount };
    },
  });

  res.json(result);
});

export function startEmbeddedHandlerServer(port: number) {
  app.listen(port);
}
