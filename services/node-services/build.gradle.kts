plugins { id("com.palantir.docker") version "0.35.0" }

docker {
  name = "restatedev/e2e-node-services"

  files(
      ".dockerignore",
      ".eslintignore",
      ".eslintrc.json",
      "package.json",
      "package-lock.json",
      "tsconfig.json")
  copySpec.with(
      copySpec {
        from(".") {
          include("src/**")
          into("src")
        }
      })
  buildArgs(mapOf("NPM_TOKEN" to System.getenv("GH_PACKAGE_READ_ACCESS_TOKEN")))
}
