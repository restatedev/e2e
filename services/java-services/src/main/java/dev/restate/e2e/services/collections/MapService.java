// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.services.collections;

import static dev.restate.e2e.services.collections.map.MapProto.*;

import dev.restate.e2e.services.collections.map.MapServiceRestate;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import java.util.Collection;

public class MapService extends MapServiceRestate.MapServiceRestateImplBase {

  @Override
  public void set(ObjectContext context, SetRequest request) throws TerminalException {
    context.set(StateKey.string(request.getKey()), request.getValue());
  }

  @Override
  public GetResponse get(ObjectContext context, GetRequest request) throws TerminalException {
    return GetResponse.newBuilder()
        .setValue(context.get(StateKey.string(request.getKey())).orElse(""))
        .build();
  }

  @Override
  public ClearAllResponse clearAll(ObjectContext context, ClearAllRequest request)
      throws TerminalException {
    Collection<String> keys = context.stateKeys();
    context.clearAll();
    return ClearAllResponse.newBuilder().addAllKeys(keys).build();
  }
}
