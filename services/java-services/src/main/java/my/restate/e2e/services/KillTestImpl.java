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
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.CoreSerdes;

public class KillTestImpl {

  public static class RunnerImpl implements KillTest.Runner {

    // The call tree method invokes the KillSingletonService::recursiveCall which blocks on calling
    // itself again.
    // This will ensure that we have a call tree that is two calls deep and has a pending invocation
    // in the inbox:
    // startCallTree --> recursiveCall --> recursiveCall:inboxed
    @Override
    public void startCallTree(Context context) {
      KillTestSingletonClient.fromContext(context, "").recursiveCall().await();
    }
  }

  public static class SingletonImpl implements KillTest.Singleton {

    @Override
    public void recursiveCall(ObjectContext context) {
      var awakeable = context.awakeable(CoreSerdes.RAW);
      AwakeableHolderClient.fromContext(context, "kill").send().hold(awakeable.id());

      awakeable.await();

      KillTestSingletonClient.fromContext(context, "").recursiveCall().await();
    }

    @Override
    public void isUnlocked(ObjectContext context) {
      // no-op
    }
  }
}
