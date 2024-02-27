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
import java.util.List;

public class CoordinatorInvokeSequentiallyRequest {
  private List<Boolean> executeAsBackgroundCall;
  private String listName;

  @JsonCreator
  public CoordinatorInvokeSequentiallyRequest(
      @JsonProperty("executeAsBackgroundCall") List<Boolean> executeAsBackgroundCall,
      @JsonProperty("listName") String listName) {
    this.executeAsBackgroundCall = executeAsBackgroundCall;
    this.listName = listName;
  }

  public List<Boolean> getExecuteAsBackgroundCall() {
    return executeAsBackgroundCall;
  }

  public void setExecuteAsBackgroundCall(List<Boolean> executeAsBackgroundCall) {
    this.executeAsBackgroundCall = executeAsBackgroundCall;
  }

  public String getListName() {
    return listName;
  }

  public void setListName(String listName) {
    this.listName = listName;
  }
}
