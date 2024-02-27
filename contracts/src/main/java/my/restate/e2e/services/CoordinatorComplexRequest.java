// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
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

public class CoordinatorComplexRequest {
  private long sleepDurationMillis;
  private String requestValue;

  @JsonCreator
  public CoordinatorComplexRequest(
      @JsonProperty("sleepDurationMillis") long sleepDurationMillis,
      @JsonProperty("requestValue") String requestValue) {
    this.sleepDurationMillis = sleepDurationMillis;
    this.requestValue = requestValue;
  }

  public long getSleepDurationMillis() {
    return sleepDurationMillis;
  }

  public void setSleepDurationMillis(long sleepDurationMillis) {
    this.sleepDurationMillis = sleepDurationMillis;
  }

  public String getRequestValue() {
    return requestValue;
  }

  public void setRequestValue(String requestValue) {
    this.requestValue = requestValue;
  }
}
