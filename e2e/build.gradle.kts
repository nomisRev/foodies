import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.testballoon)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xcontext-parameters") }
}

dependencies {
    testImplementation(libs.playwright)
    testImplementation(libs.arrow.core)
    testImplementation(libs.arrow.fx.coroutines)
    
    // TestBalloon
    testImplementation(libs.testballoon)

    // Standard testing
    testImplementation(libs.logback)
}

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = sourceSets["test"].runtimeClasspath
    args = listOf("install")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
}
