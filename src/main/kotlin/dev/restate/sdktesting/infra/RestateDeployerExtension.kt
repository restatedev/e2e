// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.infra

import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.AnnotationSupport

class RestateDeployerExtension(
    private val deployerFactory: (RestateDeployer.Builder.() -> Unit)? = null
) : BeforeAllCallback, BaseRestateDeployerExtension() {

  override fun beforeAll(context: ExtensionContext) {
    val builder = RestateDeployer.builder()

    val className =
        (context.enclosingTestClasses.map { it.simpleName } +
                listOf(context.requiredTestClass.simpleName))
            .joinToString("$")

    val deployerFactory =
        this.deployerFactory
            ?: (AnnotationSupport.findAnnotatedFieldValues(
                    context.requiredTestInstance, Deployer::class.java)
                .firstOrNull() as? RestateDeployer.Builder.() -> Unit)
    if (deployerFactory == null) {
      throw IllegalStateException("The class $className has no deployer factory configured")
    }

    deployerFactory.invoke(builder)
    val deployer = builder.build(RestateDeployer.reportDirectory(getReportPath(context), className))
    context.getStore(NAMESPACE).put(DEPLOYER_KEY, deployer)
    deployer.deployAll()
  }
}
