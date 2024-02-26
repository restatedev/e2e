// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.VirtualObject;
import java.util.List;

@VirtualObject
public interface MapObject {

  class Entry {
    private final String key;
    private final String value;

    @JsonCreator
    public Entry(@JsonProperty("key") String key, @JsonProperty("value") String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }

  @Handler
  void set(ObjectContext context, Entry entry);

  @Handler
  String get(ObjectContext context, String key);

  @Handler
  List<String> clearAll(ObjectContext objectContext);
}
