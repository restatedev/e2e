package dev.restate.e2e.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class FunctionSpecTest {

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
    assertThat(FunctionSpec.builder(containerImageName).build().hostName).isEqualTo("runtime")
  }
}
