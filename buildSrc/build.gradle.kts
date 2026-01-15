plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Access the version catalogs
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val ktorLibs = extensions.getByType<VersionCatalogsExtension>().named("ktorLibs")

dependencies {
    val kotlinVersion = libs.findVersion("kotlin").get().requiredVersion
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-power-assert:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    
    val testballoonVersion = libs.findVersion("testballoon").get().requiredVersion
    implementation("de.infix.testBalloon:testBalloon-gradle-plugin:$testballoonVersion")
    
    val shadowVersion = libs.findVersion("shadow").get().requiredVersion
    implementation("com.gradleup.shadow:shadow-gradle-plugin:$shadowVersion")

    val ktorVersion = ktorLibs.findVersion("ktor").get().requiredVersion
    implementation("io.ktor.plugin:io.ktor.plugin.gradle.plugin:$ktorVersion")
}
