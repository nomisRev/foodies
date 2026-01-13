package com.foodies.e2e

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Test configuration that can be customized via command-line arguments or environment variables.
 *
 * Usage:
 *   ./gradlew :e2e:test --args="--webappBaseUrl=http://localhost:8080 --headless=false"
 */
data class E2EConfig(
    // Storage state for authenticated sessions
    val storageStatePath: Path = Paths.get("build/playwright/.auth/user.json"),

    // Base URLs
    val webappBaseUrl: String = "http://foodies.local",
    val apiBaseUrl: String = webappBaseUrl,

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
        fun fromEnvironment(): E2EConfig {
            val isCI = System.getenv("CI")?.toBoolean() ?: false
            val webappBaseUrl = System.getenv("WEBAPP_BASE_URL") ?: "http://foodies.local"
            return E2EConfig(
                webappBaseUrl = webappBaseUrl,
                apiBaseUrl = System.getenv("API_BASE_URL") ?: webappBaseUrl,
                testUsername = System.getenv("TEST_USERNAME") ?: "food_lover",
                testPassword = System.getenv("TEST_PASSWORD") ?: "password",
                keycloakBaseUrl = System.getenv("KEYCLOAK_BASE_URL") ?: "http://foodies.local:8000",
                isCI = isCI,
                headless = System.getenv("HEADLESS")?.toBoolean() ?: isCI,
                slowMo = System.getenv("SLOW_MO")?.toIntOrNull() ?: 0
            )
        }
    }
}
