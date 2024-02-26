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
public interface Failing {

  @Handler
  void terminallyFailingCall(ObjectContext context, String errorMessage);

  @Handler
  String callTerminallyFailingCall(ObjectContext context, String errorMessage);

  @Handler
  String invokeExternalAndHandleFailure(ObjectContext context);

  @Handler
  int failingCallWithEventualSuccess(ObjectContext context);

  @Handler
  int failingSideEffectWithEventualSuccess(ObjectContext context);

  @Handler
  void terminallyFailingSideEffect(ObjectContext context, String errorMessage);
}
