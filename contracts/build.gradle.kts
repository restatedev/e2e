// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

plugins { java }

dependencies {
  annotationProcessor(libs.restate.sdk.api.gen)

  api(libs.restate.sdk.api)
  api(libs.restate.sdk.workflow.api)
  api(libs.restate.sdk.jackson)

  implementation(libs.jackson.parameter.names)
}

tasks.withType<JavaCompile> {
  // Using -parameters allows to use Jackson ParameterName feature
  // https://github.com/FasterXML/jackson-modules-java8/tree/2.14/parameter-names
  options.compilerArgs.add("-parameters")
}
