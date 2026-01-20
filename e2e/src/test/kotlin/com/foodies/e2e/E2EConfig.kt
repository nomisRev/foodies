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
    val storageStatePath: Path = Paths.get("build/playwright/.auth/user.json"),
    val webappBaseUrl: String = "http://foodies.local",
    val apiBaseUrl: String = webappBaseUrl,
    val testUsername: String = "food_lover@gmail.com",
    val testPassword: String = "password",
    val headless: Boolean = false,
    val slowMo: Int = 0,
    val defaultTimeout: Int = 30_000, // 30 seconds
    val navigationTimeout: Int = 60_000 // 60 seconds
) {
    companion object {
        fun fromEnvironment(): E2EConfig {
            val webappBaseUrl = System.getenv("WEBAPP_BASE_URL") ?: "http://foodies.local"
            return E2EConfig(
                webappBaseUrl = webappBaseUrl,
                apiBaseUrl = System.getenv("API_BASE_URL") ?: webappBaseUrl,
                testUsername = System.getenv("TEST_USERNAME") ?: "food_lover@gmail.com",
                testPassword = System.getenv("TEST_PASSWORD") ?: "password",
                headless = System.getenv("HEADLESS")?.toBoolean() ?: false,
                slowMo = System.getenv("SLOW_MO")?.toIntOrNull() ?: 0
            )
        }
    }
}
