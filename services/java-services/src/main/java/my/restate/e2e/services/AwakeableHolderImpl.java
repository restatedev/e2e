// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
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
import dev.restate.sdk.common.TerminalException;

public class AwakeableHolderImpl implements AwakeableHolder {

  private static final StateKey<String> ID_KEY = StateKey.of("id", JsonSerdes.STRING);

  @Override
  public void hold(ObjectContext context, String id) {
    context.set(ID_KEY, id);
  }

  @Override
  public boolean hasAwakeable(ObjectContext context) {
    return context.get(ID_KEY).isPresent();
  }

  @Override
  public void unlock(ObjectContext context, String payload) {
    String awakeableId =
        context.get(ID_KEY).orElseThrow(() -> new TerminalException("No awakeable registered"));
    context.awakeableHandle(awakeableId).resolve(JsonSerdes.STRING, payload);
  }
}
