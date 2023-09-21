import * as restate from "@restatedev/restate-sdk";

export const HandlerAPIEchoTestFQN = "handlerapi.HandlerAPIEchoTest";

const COUNTER_KEY = "counter";

// These two handlers just test the correct propagation of the input message in the output
const echo = (ctx: restate.RpcContext, msg: any): Promise<any> => {
  return msg;
};
const echoEcho = async (ctx: restate.RpcContext, msg: any): Promise<any> => {
  return await ctx.rpc(handlerApi).echo(msg);
};

const handlerApi: restate.ServiceApi<typeof HandlerAPIEchoRouter> = {
  path: HandlerAPIEchoTestFQN,
};

export const HandlerAPIEchoRouter = restate.router({
  echo,
  echoEcho,
});

// -- Counter service

const get = async (
  ctx: restate.RpcContext,
  key: string,
  value: any
): Promise<{ counter: number }> => {
  console.log("get: " + JSON.stringify(key) + " " + JSON.stringify(value));

  const counter = (await ctx.get<number>(COUNTER_KEY)) || 0;

  return { counter };
};

const handleEvent = async (ctx: restate.RpcContext, request: restate.Event) => {
  console.log("handleEvent: " + JSON.stringify(request));

  const value = (await ctx.get<number>(COUNTER_KEY)) || 0;
  const eventValue = parseInt(new TextDecoder().decode(request.body()));
  console.log("Event value: " + eventValue);
  ctx.set(COUNTER_KEY, value + eventValue);
};

export const CounterHandlerAPIFQN = "handlerapi.Counter";

export const CounterHandlerAPIRouter = restate.keyedRouter({
  get,
  handleEvent: restate.keyedEventHandler(handleEvent),
});
