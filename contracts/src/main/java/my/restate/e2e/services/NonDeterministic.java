// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;

@VirtualObject
public interface NonDeterministic {

  @Handler
  void leftSleepRightCall(ObjectContext context);

  @Handler
  void callDifferentMethod(ObjectContext context);

  @Handler
  void backgroundInvokeWithDifferentTargets(ObjectContext context);

  @Handler
  void setDifferentKey(ObjectContext context);
}
