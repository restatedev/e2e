// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.canceltest;

import dev.restate.sdk.RestateContext;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;

public class CancelTestService extends CancelTestServiceRestate.CancelTestServiceRestateImplBase {
  private static StateKey<Boolean> CANCELED_STATE =
      StateKey.of("canceled", CoreSerdes.JSON_BOOLEAN);

  @Override
  public void startTest(RestateContext ctx, CancelTestProto.BlockingRequest request)
      throws TerminalException {
    BlockingServiceRestate.BlockingServiceRestateClient client =
        BlockingServiceRestate.newClient(ctx);

    try {
      client.block(request).await();
    } catch (TerminalException e) {
      if (e.getCode() == TerminalException.Code.CANCELLED) {
        ctx.set(CANCELED_STATE, true);
      } else {
        throw e;
      }
    }
  }

  @Override
  public CancelTestProto.Response verifyTest(RestateContext context) throws TerminalException {
    return CancelTestProto.Response.newBuilder()
        .setIsCanceled(context.get(CANCELED_STATE).orElse(false))
        .build();
  }
}
