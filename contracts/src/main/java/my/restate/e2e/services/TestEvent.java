// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import java.util.Objects;

public class TestEvent {

  private final String id;
  private final long value;

  public TestEvent(String id, long value) {
    this.id = id;
    this.value = value;
  }

  public String getId() {
    return id;
  }

  public long getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TestEvent testEvent = (TestEvent) o;
    return value == testEvent.value && Objects.equals(id, testEvent.id);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(id);
    result = 31 * result + Long.hashCode(value);
    return result;
  }

  @Override
  public String toString() {
    return "TestEvent{" + "id='" + id + '\'' + ", value=" + value + '}';
  }
}
