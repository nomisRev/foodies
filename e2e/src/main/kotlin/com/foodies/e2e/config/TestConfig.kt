package com.foodies.e2e.config

/**
 * Configuration for E2E tests.
 * Values can be provided via environment variables or command-line arguments.
 */
data class TestConfig(
    val webappBaseUrl: String,
    val apiBaseUrl: String,
    val headless: Boolean = true,
    val slowMo: Double = 0.0,
    val screenshotOnFailure: Boolean = true,
    val saveVideo: Boolean = false
) {
    companion object {
        /**
         * Loads configuration from environment variables.
         */
        fun fromEnvironment(): TestConfig {
            return TestConfig(
                webappBaseUrl = getSetting("WEBAPP_BASE_URL", "webappBaseUrl") ?: "http://localhost:8080",
                apiBaseUrl = getSetting("API_BASE_URL", "apiBaseUrl") ?: "http://localhost:8080",
                headless = getSetting("HEADLESS", "headless")?.toBoolean() ?: true,
                slowMo = getSetting("SLOW_MO", "slowMo")?.toDouble() ?: 0.0,
                screenshotOnFailure = getSetting("SCREENSHOT_ON_FAILURE", "screenshotOnFailure")?.toBoolean() ?: true,
                saveVideo = getSetting("SAVE_VIDEO", "saveVideo")?.toBoolean() ?: false
            )
        }

        private fun getSetting(envName: String, propName: String): String? {
            return System.getenv(envName) ?: System.getProperty(propName)
        }

        /**
         * Parses command-line arguments into a TestConfig.
         * Useful for local development.
         */
        fun fromArgs(args: Array<String>): TestConfig {
            val argMap = parseArgs(args)
            val base = fromEnvironment()
            
            return base.copy(
                webappBaseUrl = argMap["webappBaseUrl"] ?: base.webappBaseUrl,
                apiBaseUrl = argMap["apiBaseUrl"] ?: base.apiBaseUrl,
                headless = argMap["headless"]?.toBoolean() ?: base.headless,
                slowMo = argMap["slowMo"]?.toDouble() ?: base.slowMo,
                screenshotOnFailure = argMap["screenshotOnFailure"]?.toBoolean() ?: base.screenshotOnFailure,
                saveVideo = argMap["saveVideo"]?.toBoolean() ?: base.saveVideo
            )
        }

        private fun parseArgs(args: Array<String>): Map<String, String> {
            return args.filter { it.startsWith("--") }
                .associate {
                    val parts = it.substring(2).split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to "true"
                }
        }
    }
}
