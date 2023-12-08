// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class ServiceDeployerTest {

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "runtime",
              "runtime:main",
              "ghcr.io/restatedev/runtime",
              "restatedev/runtime:main",
              "ghcr.io/restatedev/runtime:main"])
  fun correctHostNameInference(containerImageName: String) {
    assertThat(ServiceDeployer.builder(containerImageName).build().name).isEqualTo("runtime")
  }
}
