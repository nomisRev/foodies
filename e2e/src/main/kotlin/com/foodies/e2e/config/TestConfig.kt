package com.foodies.e2e.config

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Test configuration that can be customized via command-line arguments or environment variables.
 *
 * Usage:
 *   ./gradlew :e2e:test --args="--webappBaseUrl=http://localhost:8080 --headless=false"
 */
data class TestConfig(
    // Storage state for authenticated sessions
    val storageStatePath: Path = Paths.get("build/playwright/.auth/user.json"),

    // Base URLs
    val webappBaseUrl: String = "http://foodies.local:8080",
    val apiBaseUrl: String = webappBaseUrl,
    val menuServiceUrl: String = "http://foodies.local:8082",
    val profileServiceUrl: String = "http://foodies.local:8081",
    val basketServiceUrl: String = "http://foodies.local:8083",
    val kmpWebBaseUrl: String = "http://foodies.local:8081",

    // Test credentials
    val testUsername: String = "food_lover",
    val testPassword: String = "password",

    // Keycloak configuration
    val keycloakBaseUrl: String = "http://foodies.local:8000",

    // CI flag
    val isCI: Boolean = false,

    // Browser configuration
    val headless: Boolean = isCI,
    val slowMo: Int = 0,

    // Timeouts
    val defaultTimeout: Int = 30_000, // 30 seconds
    val navigationTimeout: Int = 60_000 // 60 seconds
) {
    companion object {
        /**
         * Creates a TestConfig from environment variables with fallback to defaults.
         */
        fun fromEnvironment(): TestConfig {
            val isCI = System.getenv("CI")?.toBoolean() ?: false
            val webappBaseUrl = System.getenv("WEBAPP_BASE_URL") ?: "http://foodies.local:8080"
            return TestConfig(
                webappBaseUrl = webappBaseUrl,
                apiBaseUrl = System.getenv("API_BASE_URL") ?: webappBaseUrl,
                menuServiceUrl = System.getenv("MENU_SERVICE_URL") ?: "http://foodies.local:8082",
                profileServiceUrl = System.getenv("PROFILE_SERVICE_URL") ?: "http://foodies.local:8081",
                basketServiceUrl = System.getenv("BASKET_SERVICE_URL") ?: "http://foodies.local:8083",
                kmpWebBaseUrl = System.getenv("KMP_WEB_BASE_URL") ?: "http://foodies.local:8081",
                testUsername = System.getenv("TEST_USERNAME") ?: "food_lover",
                testPassword = System.getenv("TEST_PASSWORD") ?: "password",
                keycloakBaseUrl = System.getenv("KEYCLOAK_BASE_URL") ?: "http://foodies.local:8000",
                isCI = isCI,
                headless = System.getenv("HEADLESS")?.toBoolean() ?: isCI,
                slowMo = System.getenv("SLOW_MO")?.toIntOrNull() ?: 0
            )
        }

        /**
         * Parses command-line arguments to create a TestConfig.
         * Supports both --key=value and --key value formats.
         */
        fun fromArgs(args: Array<String>): TestConfig {
            val argMap = parseArgs(args)
            val base = fromEnvironment()

            return TestConfig(
                storageStatePath = argMap["storageStatePath"]?.let { Paths.get(it) } ?: base.storageStatePath,
                webappBaseUrl = argMap["webappBaseUrl"] ?: base.webappBaseUrl,
                apiBaseUrl = argMap["apiBaseUrl"] ?: (argMap["webappBaseUrl"] ?: base.apiBaseUrl),
                menuServiceUrl = argMap["menuServiceUrl"] ?: base.menuServiceUrl,
                profileServiceUrl = argMap["profileServiceUrl"] ?: base.profileServiceUrl,
                basketServiceUrl = argMap["basketServiceUrl"] ?: base.basketServiceUrl,
                kmpWebBaseUrl = argMap["kmpWebBaseUrl"] ?: base.kmpWebBaseUrl,
                testUsername = argMap["testUsername"] ?: base.testUsername,
                testPassword = argMap["testPassword"] ?: base.testPassword,
                keycloakBaseUrl = argMap["keycloakBaseUrl"] ?: base.keycloakBaseUrl,
                isCI = argMap["ci"]?.toBoolean() ?: base.isCI,
                headless = argMap["headless"]?.toBoolean() ?: base.headless,
                slowMo = argMap["slowMo"]?.toIntOrNull() ?: base.slowMo,
                defaultTimeout = argMap["defaultTimeout"]?.toIntOrNull() ?: base.defaultTimeout,
                navigationTimeout = argMap["navigationTimeout"]?.toIntOrNull() ?: base.navigationTimeout
            )
        }

        private fun parseArgs(args: Array<String>): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                if (arg.startsWith("--")) {
                    val key = arg.removePrefix("--")
                    if (key.contains("=")) {
                        val (k, v) = key.split("=", limit = 2)
                        result[k] = v
                    } else if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                        result[key] = args[i + 1]
                        i++
                    }
                }
                i++
            }
            return result
        }
    }
}
