package dev.restate.e2e.utils

import org.junit.jupiter.api.extension.*

class RestateDeployerExtension(private val deployer: RestateDeployer) :
    BeforeAllCallback, BaseRestateDeployerExtension() {

  override fun beforeAll(context: ExtensionContext) {
    deployer.deployAll(RestateDeployer.reportDirectory(context.requiredTestClass))
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer)
  }
}
