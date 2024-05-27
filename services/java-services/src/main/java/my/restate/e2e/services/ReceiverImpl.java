// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.JsonSerdes;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;

public class ReceiverImpl implements Receiver {

  public static final StateKey<String> STATE_KEY = StateKey.of("my-state", JsonSerdes.STRING);

  @Override
  public String ping(ObjectContext context) {
    return "pong";
  }

  @Override
  public void setValue(ObjectContext context, String value) {
    context.set(STATE_KEY, value);
  }

  @Override
  public String getValue(ObjectContext context) {
    return context.get(STATE_KEY).orElse("");
  }
}
