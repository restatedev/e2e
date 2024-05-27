// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.Context;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.Target;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualObjectProxyImpl implements VirtualObjectProxy {

  private final ConcurrentHashMap<Request, Integer> retryCounts = new ConcurrentHashMap<>();

  @Override
  public byte[] call(Context context, Request request) {
    retryCounts.compute(request, (k, v) -> v == null ? 1 : v + 1);
    return context
        .call(
            Target.virtualObject(
                request.getComponentName(), request.getKey(), request.getHandlerName()),
            Serde.RAW,
            Serde.RAW,
            request.getMessage())
        .await();
  }

  @Override
  public void oneWayCall(Context context, Request request) {
    retryCounts.compute(request, (k, v) -> v == null ? 1 : v + 1);
    context.send(
        Target.virtualObject(
            request.getComponentName(), request.getKey(), request.getHandlerName()),
        Serde.RAW,
        request.getMessage());
  }

  @Override
  public int getRetryCount(Context context, Request request) {
    return retryCounts.get(request);
  }
}
