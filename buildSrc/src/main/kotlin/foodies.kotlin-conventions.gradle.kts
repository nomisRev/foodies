plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.power-assert")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("de.infix.testBalloon")
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(
            "**/build/**/*.kt",
            "**/RedisBasketRepositorySpec.kt",
            "**/TestSyntax.kt",
            "**/UserSessionScope.kt",
            "**/OpenTelemetry.kt",
            "**/UserAuthFlowSpec.kt",
            "**/Utils.kt",
            "**/PostgreSQLContainer.kt",
            "**/TestSuiteWithContext.kt",
            "**/TestApplication.kt"
        )
        ktfmt("0.54").kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt("0.54").kotlinlangStyle()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
        )
    }
}
