// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.Awakeable;
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.common.CoreSerdes;
import java.util.Objects;

public class UpgradeTestImpl implements UpgradeTest {

  // Value should be either "v1" or "v2"
  private final String version =
      Objects.requireNonNull(System.getenv("E2E_UPGRADETEST_VERSION")).trim();

  @Override
  public String executeSimple(Context context) {
    return version;
  }

  @Handler
  public String executeComplex(Context ctx) {
    if (!"v1".equals(version)) {
      throw new IllegalStateException(
          "executeComplex should not be invoked with version different from 1!");
    }

    // In v1 case we create an awakeable, we ask the AwakeableHolderService to hold it, and then we
    // await on it
    Awakeable<String> awakeable = ctx.awakeable(CoreSerdes.JSON_STRING);
    AwakeableHolderClient.fromContext(ctx, "upgrade").send().hold(awakeable.id());
    awakeable.await();

    // Store the result in List service, because this service is invoked with
    // dev.restate.Ingress#Invoke
    ListObjectClient.fromContext(ctx, "upgrade-test").send().append(version);

    return version;
  }
}
