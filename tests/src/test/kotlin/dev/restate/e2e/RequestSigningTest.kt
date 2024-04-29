// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e

import dev.restate.e2e.utils.InjectIngressClient
import dev.restate.e2e.utils.RestateDeployer
import dev.restate.e2e.utils.RestateDeployerExtension
import dev.restate.sdk.client.IngressClient
import java.util.*
import my.restate.e2e.services.CounterClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

const val E2E_REQUEST_SIGNING_ENV = "E2E_REQUEST_SIGNING"
const val PRIVATE_KEY =
    """
-----BEGIN PRIVATE KEY-----
MC4CAQAwBQYDK2VwBCIEIHsQRVQ+AZX9/Yy1b0Zw+OA+bb7xDxGsAd5kB45jZhoc
-----END PRIVATE KEY-----
"""
const val SIGNING_KEY = "publickeyv1_ChjENKeMvCtRnqG2mrBK1HmPKufgFUc98K8B3ononQvp"

class NodeRequestSigningTest : BaseRequestSigningTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withCopyToContainer("/a.pem", PRIVATE_KEY)
                .withEnv("RESTATE_REQUEST_IDENTITY_PRIVATE_KEY_PEM_FILE", "/a.pem")
                .withServiceEndpoint(
                    Containers.NODE_COUNTER_SERVICE_SPEC.toBuilder()
                        .withEnv(E2E_REQUEST_SIGNING_ENV, SIGNING_KEY)
                        .build())
                .build())
  }
}

class JavaRequestSigningTest : BaseRequestSigningTest() {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension =
        RestateDeployerExtension(
            RestateDeployer.Builder()
                .withCopyToContainer("/a.pem", PRIVATE_KEY)
                .withEnv("RESTATE_REQUEST_IDENTITY_PRIVATE_KEY_PEM_FILE", "/a.pem")
                .withServiceEndpoint(
                    Containers.JAVA_COUNTER_SERVICE_SPEC.toBuilder()
                        .withEnv(E2E_REQUEST_SIGNING_ENV, SIGNING_KEY)
                        .build())
                .build())
  }
}

abstract class BaseRequestSigningTest {

  @Test
  @Execution(ExecutionMode.CONCURRENT)
  fun requestSigningPass(@InjectIngressClient ingressClient: IngressClient) {
    val counterName = UUID.randomUUID().toString()
    val client = CounterClient.fromIngress(ingressClient, counterName)

    client.add(1)
    assertThat(client.get()).isEqualTo(1)
  }
}
