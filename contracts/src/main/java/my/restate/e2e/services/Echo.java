package my.restate.e2e.services;

import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;

@Service(name = "Echo")
public interface Echo {

  @Handler
  String blockThenEcho(Context ctx, String awakeableKey);
}
