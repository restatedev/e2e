import { RestateContext, useContext } from "@restatedev/restate-sdk";
import {
  BackgroundCallRequest,
  CallRequest,
  Command_AsyncCall,
  Command_AsyncCallAwait,
  Command_BackgroundCall,
  Command_Sleep,
  Command_SyncCall,
  CommandInterpreter,
  CommandInterpreterClientImpl,
  Commands,
  Empty,
  Key,
  protobufPackage,
  TestParams,
  VerificationRequest,
  VerificationResult,
} from "./generated/interpreter";

export const CommandInterpreterServiceFQN =
  protobufPackage + ".CommandInterpreter";

export class CommandInterpreterService implements CommandInterpreter {
  async call(request: CallRequest): Promise<Empty> {
    return this.eitherCall(request.key, request.commands);
  }

  async backgroundCall(request: BackgroundCallRequest): Promise<Empty> {
    return this.eitherCall(request.key, request.commands);
  }

  async eitherCall(
    key: Key | undefined,
    commands: Commands | undefined
  ): Promise<Empty> {
    if (!commands?.command) {
      throw new Error("CallRequest with no commands");
    }
    if (!key) {
      throw new Error("CallRequest with no key");
    }
    if (!key.params) {
      throw new Error("CallRequest with no test parameters");
    }
    const ctx = useContext(this);
    const client = new CommandInterpreterClientImpl(ctx);
    const pending_calls = new Map<number, Promise<Empty>>();

    for (const c of commands.command) {
      switch (true) {
        case c.increment !== undefined:
          await this._increment(ctx);
          break;
        case c.syncCall !== undefined:
          await this._syncCall(
            ctx,
            client,
            key.params,
            c.syncCall as Command_SyncCall
          );
          break;
        case c.asyncCall !== undefined:
          this._asyncCall(
            ctx,
            client,
            pending_calls,
            key.params,
            c.asyncCall as Command_AsyncCall
          );
          break;
        case c.asyncCallAwait !== undefined:
          await this._asyncCallAwait(
            ctx,
            pending_calls,
            c.asyncCallAwait as Command_AsyncCallAwait
          );
          break;
        case c.backgroundCall !== undefined:
          await this._backgroundCall(
            ctx,
            client,
            key.params,
            c.backgroundCall as Command_BackgroundCall
          );
          break;
        case c.sleep !== undefined:
          await this._sleep(ctx, c.sleep as Command_Sleep);
          break;
        default:
          // should be unreachable
          throw new Error("Empty Command in CallRequest");
      }
    }

    return Empty.create({});
  }

  async _increment(ctx: RestateContext): Promise<void> {
    const counter = (await ctx.get<number>("counter")) || 0;
    return ctx.set("counter", counter + 1);
  }

  async _syncCall(
    ctx: RestateContext,
    client: CommandInterpreterClientImpl,
    params: TestParams,
    request: Command_SyncCall
  ): Promise<void> {
    await client.call(
      CallRequest.create({
        key: { params, target: request.target },
        commands: request.commands,
      })
    );
  }

  _asyncCall(
    ctx: RestateContext,
    client: CommandInterpreterClientImpl,
    pending_calls: Map<number, Promise<Empty>>,
    params: TestParams,
    request: Command_AsyncCall
  ) {
    pending_calls.set(
      request.callId,
      client.call(
        CallRequest.create({
          key: { params, target: request.target },
          commands: request.commands,
        })
      )
    );
  }

  async _asyncCallAwait(
    ctx: RestateContext,
    pending_calls: Map<number, Promise<Empty>>,
    request: Command_AsyncCallAwait
  ): Promise<void> {
    const p = pending_calls.get(request.callId);
    if (p === undefined) {
      throw new Error("Unrecognised CallID in AsyncCallAwait command");
    }
    await p;
    return;
  }

  async _backgroundCall(
    ctx: RestateContext,
    client: CommandInterpreterClientImpl,
    params: TestParams,
    request: Command_BackgroundCall
  ): Promise<void> {
    return ctx.oneWayCall(() =>
      client.backgroundCall(
        BackgroundCallRequest.create({
          key: { params, target: request.target },
          commands: request.commands,
        })
      )
    );
  }

  async _sleep(ctx: RestateContext, request: Command_Sleep): Promise<void> {
    return ctx.sleep(request.milliseconds);
  }

  async verify(request: VerificationRequest): Promise<VerificationResult> {
    const ctx = useContext(this);
    return VerificationResult.create({
      expected: request.expected,
      actual: (await ctx.get<number>("counter")) || 0,
    });
  }

  async clear(): Promise<Empty> {
    const ctx = useContext(this);

    await ctx.clear("counter");

    return Empty.create({});
  }
}
