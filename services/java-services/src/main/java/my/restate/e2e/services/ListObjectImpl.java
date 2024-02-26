// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListObjectImpl implements ListObject {

  private static final StateKey<List<String>> LIST_KEY =
      StateKey.of("list", JacksonSerdes.of(new TypeReference<>() {}));

  @Override
  public void append(ObjectContext context, String value) {
    List<String> list = context.get(LIST_KEY).orElseGet(() -> new ArrayList<>(1));
    list.add(value);
    context.set(LIST_KEY, list);
  }

  @Override
  public List<String> get(ObjectContext context) {
    return context.get(LIST_KEY).orElse(Collections.emptyList());
  }

  @Override
  public List<String> clear(ObjectContext context) {
    List<String> result = context.get(LIST_KEY).orElse(Collections.emptyList());
    context.clear(LIST_KEY);
    return result;
  }
}
