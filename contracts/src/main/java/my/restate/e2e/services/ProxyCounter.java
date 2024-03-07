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
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;

@Service
public interface ProxyCounter {

  class AddRequest {

    private final String counterName;
    private final long value;

    @JsonCreator
    public AddRequest(
        @JsonProperty("counterName") String counterName, @JsonProperty("value") long value) {
      this.counterName = counterName;
      this.value = value;
    }

    public String getCounterName() {
      return counterName;
    }

    public long getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "AddRequest{" + "counterName='" + counterName + '\'' + ", value=" + value + '}';
    }
  }

  @Handler
  void addInBackground(Context context, AddRequest value);
}
