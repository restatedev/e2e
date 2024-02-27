// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package my.restate.e2e.services;

import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.VirtualObject;

// This is a generic utility service that can be used in various situations where
// we need to synchronize the services with the test runner using an awakeable.
@VirtualObject
public interface AwakeableHolder {

  @Exclusive
  void hold(ObjectContext context, String id);

  @Exclusive
  boolean hasAwakeable(ObjectContext context);

  @Exclusive
  void unlock(ObjectContext context, String payload);
}
