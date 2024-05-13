package my.restate.e2e.services;

import dev.restate.sdk.Context;
import dev.restate.sdk.common.CoreSerdes;

public class EchoImpl implements Echo {
  @Override
  public String blockThenEcho(Context ctx, String awakeableKey) {
    var a = ctx.awakeable(CoreSerdes.JSON_STRING);
    AwakeableHolderClient.fromContext(ctx, awakeableKey).hold(a.id());
    return a.await();
  }
}
