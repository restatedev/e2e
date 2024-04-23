// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.Context;

public class EventHandlerImpl implements EventHandler {
  @Override
  public void handle(Context context, TestEvent event) {
    CounterClient.fromContext(context, event.getId()).send().add(event.getValue());
  }
}
