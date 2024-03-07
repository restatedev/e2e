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
import java.util.Arrays;
import java.util.Objects;

@Service
public interface VirtualObjectProxy {

  class Request {
    private final String componentName;
    private final String key;
    private final String handlerName;
    private final byte[] message;

    @JsonCreator
    public Request(
        @JsonProperty("componentName") String componentName,
        @JsonProperty("key") String key,
        @JsonProperty("handlerName") String handlerName,
        @JsonProperty("message") byte[] message) {
      this.componentName = componentName;
      this.key = key;
      this.handlerName = handlerName;
      this.message = message;
    }

    public String getComponentName() {
      return componentName;
    }

    public String getKey() {
      return key;
    }

    public String getHandlerName() {
      return handlerName;
    }

    public byte[] getMessage() {
      return message;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Request request = (Request) o;
      return Objects.equals(componentName, request.componentName)
          && Objects.equals(key, request.key)
          && Objects.equals(handlerName, request.handlerName)
          && Arrays.equals(message, request.message);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(componentName, key, handlerName);
      result = 31 * result + Arrays.hashCode(message);
      return result;
    }

    @Override
    public String toString() {
      return "Request{"
          + "componentName='"
          + componentName
          + '\''
          + ", key='"
          + key
          + '\''
          + ", handlerName='"
          + handlerName
          + '\''
          + ", message="
          + Arrays.toString(message)
          + '}';
    }
  }

  @Handler
  byte[] call(Context context, Request request);

  @Handler
  void oneWayCall(Context context, Request request);

  @Handler
  int getRetryCount(Context context, Request request);
}
