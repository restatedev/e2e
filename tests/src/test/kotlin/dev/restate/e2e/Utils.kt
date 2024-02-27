// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import com.fasterxml.jackson.databind.JsonNode
import dev.restate.e2e.utils.JsonUtils
import org.assertj.core.api.Assertions.assertThat

object Utils {

  fun doJsonRequestToService(
      restateEndpoint: String,
      service: String,
      method: String,
      reqBody: Any
  ): JsonNode {
    val res = JsonUtils.postJsonRequest("${restateEndpoint}${service}/${method}", reqBody)
    assertThat(res.statusCode()).isEqualTo(200)
    assertThat(res.headers().firstValue("content-type"))
        .get()
        .asString()
        .contains("application/json")
    return res.body()
  }
}
