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
import dev.restate.sdk.common.StateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MapObjectImpl implements MapObject {

  @Override
  public void set(ObjectContext context, Entry entry) {
    context.set(StateKey.string(entry.getKey()), entry.getValue());
  }

  @Override
  public String get(ObjectContext context, String key) {
    return context.get(StateKey.string(key)).orElse("");
  }

  @Override
  public List<String> clearAll(ObjectContext objectContext) {
    Collection<String> keys = objectContext.stateKeys();
    objectContext.clearAll();
    return new ArrayList<>(keys);
  }
}
