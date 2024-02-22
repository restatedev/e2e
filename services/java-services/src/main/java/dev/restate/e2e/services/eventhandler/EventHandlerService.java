// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.eventhandler;

import dev.restate.e2e.services.counter.CounterProto.CounterAddRequest;
import dev.restate.e2e.services.counter.CounterRestate;
import dev.restate.generated.Event;
import dev.restate.sdk.ObjectContext;

public class EventHandlerService extends EventHandlerRestate.EventHandlerRestateImplBase {

  @Override
  public void handle(ObjectContext ctx, Event event) {
    CounterRestate.newClient(ctx)
        .oneWay()
        .add(
            CounterAddRequest.newBuilder()
                .setCounterName(event.getKey().toStringUtf8())
                .setValue(Long.parseLong(event.getPayload().toStringUtf8()))
                .build());
  }
}
