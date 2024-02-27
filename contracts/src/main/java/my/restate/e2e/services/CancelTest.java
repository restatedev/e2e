// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.VirtualObject;

public interface CancelTest {

  enum BlockingOperation {
    CALL,
    SLEEP,
    AWAKEABLE
  }

  @VirtualObject
  interface Runner {
    @Exclusive
    void startTest(ObjectContext context, BlockingOperation operation);

    @Exclusive
    boolean verifyTest(ObjectContext context);
  }

  @VirtualObject
  interface BlockingService {
    @Exclusive
    void block(ObjectContext context, BlockingOperation operation);

    @Exclusive
    void isUnlocked(ObjectContext context);
  }
}
