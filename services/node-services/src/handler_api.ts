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

const add = async (
  ctx: restate.RpcContext,
  key: string,
  value: { value: number }
): Promise<{ newCounter: number; oldCounter: number }> => {
  console.log("add: " + JSON.stringify(key) + " " + JSON.stringify(value));

  const oldCounter = (await ctx.get<number>(COUNTER_KEY)) || 0;
  const newCounter = oldCounter + 1;
  ctx.set<number>(COUNTER_KEY, newCounter);

  return { oldCounter, newCounter };
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
  add,
  handleEvent: restate.keyedEventHandler(handleEvent),
});

export const CounterAPI: restate.ServiceApi<typeof CounterHandlerAPIRouter> = {
  path: "handlerapi.Counter",
};
