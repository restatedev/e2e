// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

package dev.restate.e2e.utils

import org.junit.jupiter.api.extension.*

class RestateDeployerExtension(private val deployer: RestateDeployer) :
    BeforeAllCallback, BaseRestateDeployerExtension() {

  override fun beforeAll(context: ExtensionContext) {
    deployer.deployAll(RestateDeployer.reportDirectory(context.requiredTestClass))
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer)
  }
}
