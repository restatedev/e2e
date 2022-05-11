val testReport = tasks.register<TestReport>("testReport") {
    destinationDirectory.set(file("$buildDir/reports/tests/test"))
    testResults.setFrom(subprojects.mapNotNull {
        it.tasks.findByPath("test")
    })
}

subprojects {
    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(testReport)
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        targetCompatibility = "11"
        sourceCompatibility = "11"
    }
}