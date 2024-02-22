// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import java.util.List;

@Service
public interface Coordinator {

  @Handler
  void sleep(Context context, long millisDuration);

  @Handler
  void manyTimers(Context context, List<Long> millisDurations);

  @Handler
  String proxy(Context context);

  @Handler
  String complex(Context context, CoordinatorComplexRequest complexRequest);

  @Handler
  boolean timeout(Context context, long millisDuration);

  @Handler
  void invokeSequentially(Context context, CoordinatorInvokeSequentiallyRequest request);
}
