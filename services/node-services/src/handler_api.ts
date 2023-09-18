import * as restate from "@restatedev/restate-sdk";

export const HandlerAPIEchoTestFQN = "handlerapi.HandlerAPIEchoTest";

// These two handlers just test the correct propagation of the input message in the output
const echo = (ctx: restate.RpcContext, msg: any): Promise<any> => {
  return msg;
};
const echoEcho = async (ctx: restate.RpcContext, msg: any): Promise<any> => {
  return await ctx.rpc(handlerApi).echo(msg);
};

const handlerApi: restate.ServiceApi<typeof HandlerApiEchoRouter> = {
  path: HandlerAPIEchoTestFQN,
};

export const HandlerApiEchoRouter = restate.router({
  echo,
  echoEcho,
});
