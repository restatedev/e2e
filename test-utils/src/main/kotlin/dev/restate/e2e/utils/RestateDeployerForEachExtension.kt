package dev.restate.e2e.utils

import org.junit.jupiter.api.extension.*

class RestateDeployerForEachExtension(private val deployerFactory: () -> RestateDeployer) :
    BeforeEachCallback, BaseRestateDeployerExtension() {

  override fun beforeEach(context: ExtensionContext) {
    val deployer = deployerFactory.invoke()
    deployer.deployAll(
        RestateDeployer.reportDirectory(context.requiredTestClass, context.requiredTestMethod))
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer)
  }
}
