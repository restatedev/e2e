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
import java.util.Objects;

public class CounterUpdateResponse {

  private final long oldValue;
  private final long newValue;

  @JsonCreator
  public CounterUpdateResponse(
      @JsonProperty("oldValue") long oldValue, @JsonProperty("newValue") long newValue) {
    this.oldValue = oldValue;
    this.newValue = newValue;
  }

  public long getOldValue() {
    return oldValue;
  }

  public long getNewValue() {
    return newValue;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    CounterUpdateResponse that = (CounterUpdateResponse) object;
    return oldValue == that.oldValue && newValue == that.newValue;
  }

  @Override
  public int hashCode() {
    return Objects.hash(oldValue, newValue);
  }

  @Override
  public String toString() {
    return "CounterUpdateResponse{" + "oldValue=" + oldValue + ", newValue=" + newValue + '}';
  }
}
