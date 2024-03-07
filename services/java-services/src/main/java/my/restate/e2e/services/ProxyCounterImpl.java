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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyCounterImpl implements ProxyCounter {
  private static final Logger logger = LogManager.getLogger(ProxyCounterImpl.class);

  @Override
  public void addInBackground(Context context, AddRequest value) {
    logger.info("addInBackground invoked {}", value);

    CounterClient.fromContext(context, value.getCounterName()).send().add(value.getValue());
  }
}
