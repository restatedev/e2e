// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.canceltest;

import dev.restate.e2e.services.awakeableholder.AwakeableHolderProto;
import dev.restate.e2e.services.awakeableholder.AwakeableHolderServiceRestate;
import dev.restate.sdk.Awakeable;
import dev.restate.sdk.RestateContext;
import dev.restate.sdk.common.CoreSerdes;
import dev.restate.sdk.common.TerminalException;
import java.time.Duration;

public class BlockingService extends BlockingServiceRestate.BlockingServiceRestateImplBase {
  @Override
  public void block(RestateContext context, CancelTestProto.BlockingRequest request)
      throws TerminalException {
    final BlockingServiceRestate.BlockingServiceRestateClient self =
        BlockingServiceRestate.newClient(context);
    final AwakeableHolderServiceRestate.AwakeableHolderServiceRestateClient client =
        AwakeableHolderServiceRestate.newClient(context);

    Awakeable<String> awakeable = context.awakeable(CoreSerdes.STRING_UTF8);
    client
        .hold(
            AwakeableHolderProto.HoldRequest.newBuilder()
                .setName("cancel")
                .setId(awakeable.id())
                .build())
        .await();
    awakeable.await();

    switch (request.getOperation()) {
      case CALL:
        self.block(request).await();
        break;
      case SLEEP:
        context.sleep(Duration.ofDays(1024));
        break;
      case AWAKEABLE:
        Awakeable<String> uncompletable = context.awakeable(CoreSerdes.STRING_UTF8);
        uncompletable.await();
        break;
      default:
        throw new IllegalArgumentException("Unknown operation: " + request.getOperation());
    }
  }

  @Override
  public void isUnlocked(RestateContext context) throws TerminalException {
    // no-op
  }
}
