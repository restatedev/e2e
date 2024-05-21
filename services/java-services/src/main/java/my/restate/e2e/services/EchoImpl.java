// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

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
