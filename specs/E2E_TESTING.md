# End-to-End Testing Specification for Foodies

## Overview

This specification defines the end-to-end (e2e) testing strategy for the Foodies project, based on the patterns established in the eShop reference project. The Foodies project has two distinct UI implementations that require e2e testing:

1. **HTMX-based Web Application** (Ktor server-side rendered)
2. **Kotlin Multiplatform Compose** (KMP: Android, iOS, Desktop, Web/WASM)

## Testing Framework

### Primary Framework: Playwright (Java API with Kotlin) + TestBalloon

**Rationale:** Playwright provides:
- Modern browser automation with excellent developer experience
- Built-in authentication state management
- Parallel test execution
- Rich reporting and tracing capabilities
- Cross-browser support (Chromium, Firefox, WebKit)
- Automatic waiting and retry mechanisms
- Mobile viewport emulation for responsive testing
- **Java/Kotlin API** - Full integration with Gradle and TestBalloon
- **Type safety** - Leverage Kotlin's type system for test code
- **Single language** - Write tests in the same language as the application

**TestBalloon Integration:**
- Consistent with existing test framework used throughout the project
- Descriptive test DSL with `testSuite` and `test` blocks
- Type-safe fixture management with `TestSuite.Fixture<T>`
- Test lifecycle hooks for setup/teardown
- Context receivers for ergonomic resource management
- Parallel test execution support

**Version:** Playwright 1.48.0+ with TestBalloon (as used in the project)

**Installation:**

Add to `gradle/libs.versions.toml`:
```toml
[versions]
playwright = "1.48.0"

[libraries]
playwright = { module = "com.microsoft.playwright:playwright", version.ref = "playwright" }
# TestBalloon is already in the version catalog and used throughout the project
```

Add to `e2e/build.gradle.kts`:
```kotlin
dependencies {
    testImplementation(libs.playwright)
    testImplementation(libs.bundles.testBalloon)
    testImplementation(libs.bundles.arrow)
}
```

The Playwright browsers will be installed automatically on first test run, or manually:
```bash
./gradlew :e2e:installPlaywrightBrowsers
```

## Project Structure

```
foodies/
├── e2e/                               # E2E test module
│   ├── build.gradle.kts               # Gradle build configuration
│   └── src/
│       ├── main/kotlin/
│       │   └── com/foodies/e2e/
│       │       ├── config/
│       │       │   ├── TestConfig.kt           # Test configuration object
│       │       │   └── PlaywrightConfig.kt     # Playwright setup
│       │       ├── fixtures/
│       │       │   ├── MenuData.kt             # Test data for menu items
│       │       │   └── UserData.kt             # Test user credentials
│       │       └── utils/
│       │           ├── ApiHelpers.kt           # API interaction helpers
│       │           ├── TestHelpers.kt          # Common test utilities
│       │           └── HtmxHelpers.kt          # HTMX-specific helpers
│       └── test/kotlin/
│           └── com/foodies/e2e/
│               ├── setup/
│               │   └── AuthSetup.kt            # Authentication state setup
│               ├── htmx/                       # HTMX webapp tests
│               │   ├── menu/
│               │   │   ├── BrowseMenuTest.kt   # Browse menu (unauthenticated)
│               │   │   └── MenuPaginationTest.kt # Test infinite scroll
│               │   ├── basket/
│               │   │   ├── AddToBasketTest.kt  # Add items to basket
│               │   │   ├── UpdateBasketTest.kt # Update quantities
│               │   │   └── RemoveFromBasketTest.kt # Remove items
│               │   ├── order/
│               │   │   ├── CreateOrderTest.kt  # Place order flow
│               │   │   └── OrderHistoryTest.kt # View past orders
│               │   ├── profile/
│               │   │   └── ViewProfileTest.kt  # Profile management
│               │   └── auth/
│               │       ├── LoginTest.kt        # OAuth login flow
│               │       └── LogoutTest.kt       # Logout flow
│               └── kmp/                        # Kotlin Multiplatform Compose tests
│                   ├── web/                    # Web/WASM specific tests
│                   │   └── MenuNavigationTest.kt
│                   ├── android/                # Android specific tests (future)
│                   └── ios/                    # iOS specific tests (future)
├── settings.gradle.kts                # Include e2e module
└── .github/
    └── workflows/
        ├── e2e-htmx.yml               # CI for HTMX tests
        └── e2e-kmp.yml                # CI for KMP tests
```

## TestBalloon Fixture Pattern

TestBalloon uses `TestSuite.Fixture<T>` for type-safe resource management with automatic cleanup. This eliminates global mutable state and ensures resources are properly closed after tests.

### Fixture Creation

Fixtures are created using `testFixture` and optionally paired with `closeWith` for cleanup:

```kotlin
// Simple fixture with automatic cleanup
fun TestSuite.playwrightFixture(): TestSuite.Fixture<Playwright> =
    testFixture { Playwright.create() } closeWith { close() }

// Fixture using context receiver for composition
context(suite: TestSuite)
fun Browser.authenticatedContextFixture(config: TestConfig): TestSuite.Fixture<BrowserContext> =
    suite.testFixture {
        newContext(
            Browser.NewContextOptions()
                .setStorageStatePath(config.storageStatePath)
                .setBaseURL(config.webappBaseUrl)
        )
    } closeWith { close() }
```

### Fixture Usage in Tests

Fixtures are accessed by calling them as functions. Use `with()` for ergonomic access with context receivers:

```kotlin
val authSetupSpec by testSuite {
    // Create fixtures
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = testFixture { with(playwright()) { browserFixture(config = config) } }
    val context = testFixture { with(browser()) { authenticatedContextFixture(config) } }
    val page = testFixture { with(context()) { pageFixture() } }

    test("My test") {
        // Access fixture value by calling it
        page().navigate("/")
        assertTrue(page().locator("h1").isVisible())
    }
}
```

### Benefits

- **No global state**: Each test suite creates its own fixtures
- **Automatic cleanup**: Resources are closed via `closeWith`
- **Type-safe**: Fixtures are properly typed with `TestSuite.Fixture<T>`
- **Composable**: Fixtures can depend on other fixtures using context receivers
- **Isolated**: Each test run has fresh resources

## Configuration

### Base Configuration (`e2e/src/main/kotlin/com/foodies/e2e/config/TestConfig.kt`)

```kotlin
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
            return TestConfig(
                webappBaseUrl = System.getenv("WEBAPP_BASE_URL") ?: "http://foodies.local:8080",
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
```

### Playwright Fixtures (`e2e/src/main/kotlin/com/foodies/e2e/config/PlaywrightFixtures.kt`)

```kotlin
package com.foodies.e2e.config

import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.TestSuite
import java.nio.file.Files

/**
 * Browser type for Playwright.
 */
enum class BrowserType {
    CHROMIUM, FIREFOX, WEBKIT
}

/**
 * Creates a Playwright instance fixture that will be properly closed after tests.
 */
fun TestSuite.playwrightFixture(): TestSuite.Fixture<Playwright> =
    testFixture { Playwright.create() } closeWith { close() }

/**
 * Creates a Browser instance fixture.
 */
context(suite: TestSuite)
fun Playwright.browserFixture(
    browserType: BrowserType = BrowserType.CHROMIUM,
    config: TestConfig
): TestSuite.Fixture<Browser> = suite.testFixture {
    val browserTypeInstance = when (browserType) {
        BrowserType.CHROMIUM -> chromium()
        BrowserType.FIREFOX -> firefox()
        BrowserType.WEBKIT -> webkit()
    }

    browserTypeInstance.launch(
        Browser.LaunchOptions()
            .setHeadless(config.headless)
            .setSlowMo(config.slowMo.toDouble())
    )
} closeWith { close() }

/**
 * Creates an authenticated BrowserContext fixture.
 * Will reuse existing authentication state if available.
 */
context(suite: TestSuite)
fun Browser.authenticatedContextFixture(config: TestConfig): TestSuite.Fixture<BrowserContext> =
    suite.testFixture {
        // Ensure storage state directory exists
        Files.createDirectories(config.storageStatePath.parent)

        if (Files.exists(config.storageStatePath)) {
            newContext(
                Browser.NewContextOptions()
                    .setStorageStatePath(config.storageStatePath)
                    .setBaseURL(config.webappBaseUrl)
            )
        } else {
            newContext(
                Browser.NewContextOptions()
                    .setBaseURL(config.webappBaseUrl)
            )
        }
    } closeWith { close() }

/**
 * Creates an unauthenticated BrowserContext fixture.
 */
context(suite: TestSuite)
fun Browser.unauthenticatedContextFixture(config: TestConfig): TestSuite.Fixture<BrowserContext> =
    suite.testFixture {
        newContext(
            Browser.NewContextOptions()
                .setBaseURL(config.webappBaseUrl)
        )
    } closeWith { close() }

/**
 * Creates a Page fixture from a BrowserContext.
 */
context(suite: TestSuite)
fun BrowserContext.pageFixture(): TestSuite.Fixture<Page> =
    suite.testFixture { newPage() } closeWith { close() }
```

### Gradle Build Configuration (`e2e/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.playwright)
    testImplementation(libs.bundles.testBalloon)
    testImplementation(libs.bundles.arrow)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
}

tasks.test {
    useJUnitPlatform()

    // Playwright configuration
    systemProperty("playwright.junit.enable-cache", "true")

    // Set test timeouts
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }

    // Ensure browsers are installed before running tests
    dependsOn("installPlaywrightBrowsers")

    // Pass command-line args to tests
    // Usage: ./gradlew :e2e:test --args="--headless=false --webappBaseUrl=http://localhost:8080"
    val testArgs = project.findProperty("args") as? String
    if (testArgs != null) {
        args(testArgs.split(" "))
    }
}

// Task to install Playwright browsers
tasks.register<Exec>("installPlaywrightBrowsers") {
    group = "playwright"
    description = "Installs Playwright browsers"

    commandLine("java", "-jar",
        configurations.testRuntimeClasspath.get().files
            .first { it.name.contains("playwright") }
            .absolutePath,
        "install", "chromium"
    )
}
```

### Environment Variables

Required environment variables for testing:

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `WEBAPP_BASE_URL` | HTMX webapp base URL | `http://localhost:8080` | No |
| `MENU_SERVICE_URL` | Menu service URL | `http://localhost:8082` | No |
| `PROFILE_SERVICE_URL` | Profile service URL | `http://localhost:8081` | No |
| `KMP_WEB_BASE_URL` | KMP web app URL | `http://localhost:8081` | No |
| `TEST_USERNAME` | Keycloak test username | `food_lover` | Yes |
| `TEST_PASSWORD` | Keycloak test password | `password` | Yes |
| `KEYCLOAK_BASE_URL` | Keycloak issuer URL | `http://localhost:8000` | No |
| `CI` | CI environment flag | - | Auto-set |

## Test Scenarios

### 1. HTMX Web Application Tests

#### 1.1 Authentication Tests

**File:** `e2e/src/test/kotlin/com/foodies/e2e/setup/AuthSetup.kt`

```kotlin
package com.foodies.e2e.setup

import com.foodies.e2e.config.*
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Authentication setup that runs before authenticated tests.
 * Creates and saves authentication state for reuse across tests.
 */
val authSetupSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = with(playwright()) { browserFixture(config = config) }
    val context = with(browser()) { unauthenticatedContextFixture(config) }
    val page = with(context()) { pageFixture() }

    test("Keycloak Authentication - should authenticate user and save session state") {
        // Navigate to home page
        page().navigate("/")

        // Click login button
        page().getByLabel("Sign in").click()

        // Should redirect to Keycloak
        assertTrue(page().url().contains("keycloak"), "Should redirect to Keycloak")

        // Fill login form
        page().getByLabel("Username").fill(config.testUsername)
        page().getByLabel("Password").fill(config.testPassword)
        page().getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign In")).click()

        // Should redirect back to home page
        page().waitForURL("/")
        assertTrue(page().getByText("Welcome back").isVisible(), "Should show welcome message")

        // Save authentication state
        Files.createDirectories(config.storageStatePath.parent)
        context().storageState(
            BrowserContext.StorageStateOptions()
                .setPath(config.storageStatePath)
        )
    }
}
```

**File:** `e2e/src/test/kotlin/com/foodies/e2e/htmx/auth/LogoutTest.kt`

```kotlin
package com.foodies.e2e.htmx.auth

import com.foodies.e2e.config.*
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertTrue

val logoutSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = with(playwright()) { browserFixture(config = config) }
    val context = with(browser()) { authenticatedContextFixture(config) }
    val page = with(context()) { pageFixture() }

    test("Logout Flow - should logout user and redirect to Keycloak") {
        page().navigate("/")

        // Click logout
        page().getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Logout")).click()

        // Should redirect to Keycloak logout
        assertTrue(page().url().contains("/logout"), "Should redirect to logout URL")

        // Navigate back to home
        page().navigate("/")

        // Should show login button
        assertTrue(page().getByLabel("Sign in").isVisible(), "Should show sign in button after logout")
    }
}
```

#### 1.2 Menu Browsing Tests

**File:** `e2e/src/test/kotlin/com/foodies/e2e/htmx/menu/BrowseMenuTest.kt`

```kotlin
package com.foodies.e2e.htmx.menu

import com.foodies.e2e.config.*
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertTrue

val browseMenuSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = with(playwright()) { browserFixture(config = config) }
    val context = with(browser()) { unauthenticatedContextFixture(config) }
    val page = with(context()) { pageFixture() }

    test("Browse Menu Without Authentication - should display menu items and allow viewing details") {
        page().navigate("/")

        // Should see menu items
        assertTrue(
            page().getByRole(AriaRole.HEADING, Page.GetByRoleOptions().setName("Menu")).isVisible(),
            "Should display Menu heading"
        )

        // Should see at least one menu item
        val menuItems = page().locator("[data-testid='menu-item']")
        assertTrue(menuItems.count() > 0, "Should display at least one menu item")

        // Click on first item to view details
        menuItems.first().click()

        // Should show item details
        assertTrue(
            page().locator("[data-testid='item-details']").isVisible(),
            "Should display item details"
        )
    }
}
```

**File:** `e2e/src/test/kotlin/com/foodies/e2e/htmx/menu/MenuPaginationTest.kt`

```kotlin
package com.foodies.e2e.htmx.menu

import com.foodies.e2e.config.*
import com.foodies.e2e.utils.HtmxHelpers
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertTrue

val menuPaginationSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = with(playwright()) { browserFixture(config = config) }
    val context = with(browser()) { unauthenticatedContextFixture(config) }
    val page = with(context()) { pageFixture() }

    test("Infinite Scroll Pagination - should load more items when scrolling to bottom") {
        page().navigate("/")

        // Get initial item count
        val initialCount = page().locator("[data-testid='menu-item']").count()

        // Scroll to bottom to trigger HTMX load
        page().evaluate("window.scrollTo(0, document.body.scrollHeight)")

        // Wait for HTMX swap to complete
        HtmxHelpers.waitForHtmxSwap(page())

        // Should have more items
        val updatedCount = page().locator("[data-testid='menu-item']").count()
        assertTrue(
            updatedCount > initialCount,
            "Should load more items after scrolling (initial: $initialCount, updated: $updatedCount)"
        )
    }
}
```

#### 1.3 Basket Tests

**File:** `e2e/src/test/kotlin/com/foodies/e2e/htmx/basket/AddToBasketTest.kt`

```kotlin
package com.foodies.e2e.htmx.basket

import com.foodies.e2e.config.*
import com.foodies.e2e.utils.HtmxHelpers
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val addToBasketSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = with(playwright()) { browserFixture(config = config) }
    val context = with(browser()) { authenticatedContextFixture(config) }
    val page = with(context()) { pageFixture() }

    test("Add Item to Basket - should add menu item to basket and update count") {
        page().navigate("/")

        // Click on a menu item
        page().locator("[data-testid='menu-item']").first().click()

        // Add to basket
        page().getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Add to Basket")).click()

        // Wait for HTMX swap
        HtmxHelpers.waitForHtmxSwap(page())

        // Should show success message
        assertTrue(
            page().getByText("Added to basket").isVisible(),
            "Should show success message"
        )

        // Basket count should update
        assertEquals("1", page().locator("[data-testid='basket-count']").textContent())

        // Navigate to basket
        page().getByRole(AriaRole.LINK, Page.GetByRoleOptions().setName("Basket")).click()

        // Should see item in basket
        val basketItems = page().locator("[data-testid='basket-item']")
        assertEquals(1, basketItems.count(), "Should have one item in basket")
    }
}
```

**Additional Test Files** (Follow the same TestBalloon pattern shown above):

- `UpdateBasketTest.kt` - Test updating basket item quantities via HTMX
- `RemoveFromBasketTest.kt` - Test removing items from basket

#### 1.4 Order Tests

- `CreateOrderTest.kt` - Test complete order placement flow
- `OrderHistoryTest.kt` - Test viewing order history

#### 1.5 Profile Tests

- `ViewProfileTest.kt` - Test viewing user profile information

> **Note:** All tests follow the TestBalloon pattern with `testSuite` and `test` blocks. TestSuite.Fixture<T> provides type-safe resource management with automatic cleanup via `closeWith`. Tests use:
> - **Fixtures**: `playwrightFixture()`, `browserFixture()`, `authenticatedContextFixture()`, `pageFixture()`
> - **Standard assertions**: `kotlin.test.assertTrue()`, `kotlin.test.assertEquals()`, etc.
> - **Context receivers**: `with(fixture())` for ergonomic fixture access
> - **Configuration**: `TestConfig.fromEnvironment()` or `TestConfig.fromArgs(args)` for test configuration

### 2. Kotlin Multiplatform Compose Tests

#### 2.1 Web/WASM Tests

**File:** `e2e/src/test/kotlin/com/foodies/e2e/kmp/web/MenuNavigationTest.kt`

```kotlin
package com.foodies.e2e.kmp.web

import com.foodies.e2e.config.*
import com.microsoft.playwright.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertTrue

val kmpMenuNavigationSpec by testSuite {
    val config = TestConfig.fromEnvironment()
    val playwright = playwrightFixture()
    val browser = with(playwright()) { browserFixture(config = config) }

    // Create context with KMP-specific base URL
    val context = with(browser()) {
        suite.testFixture {
            newContext(
                Browser.NewContextOptions()
                    .setBaseURL(config.kmpWebBaseUrl)
                    .setStorageStatePath(config.storageStatePath)
            )
        } closeWith { close() }
    }

    val page = with(context()) { pageFixture() }

    test("KMP Web Navigation - should navigate through menu in KMP web app") {
        page().navigate("/")

        // Should render Compose Web content
        // Note: Compose Web may use canvas rendering
        assertTrue(
            page().locator("canvas").isVisible(),
            "Should render Compose Web canvas"
        )

        // Test navigation (adjust based on actual Compose Web implementation)
        // Compose Web may require different interaction patterns

        // Click menu tab/button
        page().getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Menu")).click()

        // Should show menu items
        assertTrue(
            page().locator("[data-testid='menu-list']").isVisible(),
            "Should display menu list"
        )
    }
}
```

**Note:** KMP Compose Web/WASM tests may require special handling due to how Compose renders to canvas. Consider:
- Using accessibility selectors where possible
- Testing via API calls for complex interactions
- Screenshot comparison for UI validation
- Custom test helpers for Compose-specific interactions
- Using Playwright's `locator.screenshot()` for visual regression testing

#### 2.2 Android/iOS Tests (Future)

For native mobile platforms, consider:
- **Appium** for native app testing
- **Maestro** for mobile UI testing
- **Compose UI Testing** for Android (JVM-based)
- **XCUITest** for iOS

These will require separate configuration and test runners.

## Test Data Management

### Fixtures

**File:** `e2e/src/main/kotlin/com/foodies/e2e/fixtures/MenuData.kt`

```kotlin
package com.foodies.e2e.fixtures

import java.math.BigDecimal

data class TestMenuItem(
    val name: String,
    val description: String,
    val price: BigDecimal,
    val imageUrl: String
)

object MenuData {
    val testMenuItems = listOf(
        TestMenuItem(
            name = "Margherita Pizza",
            description = "Classic tomato and mozzarella pizza",
            price = BigDecimal("12.99"),
            imageUrl = "https://example.com/pizza.jpg"
        ),
        TestMenuItem(
            name = "Caesar Salad",
            description = "Fresh romaine with parmesan and croutons",
            price = BigDecimal("8.99"),
            imageUrl = "https://example.com/salad.jpg"
        )
        // Add more test data as needed
    )
}
```

**File:** `e2e/src/main/kotlin/com/foodies/e2e/fixtures/UserData.kt`

```kotlin
package com.foodies.e2e.fixtures

data class TestUser(
    val username: String,
    val password: String,
    val email: String
)

object UserData {
    val regular = TestUser(
        username = System.getenv("TEST_USERNAME") ?: "food_lover",
        password = System.getenv("TEST_PASSWORD") ?: "password",
        email = "food_lover@gmail.com"
    )

    val admin = TestUser(
        username = System.getenv("ADMIN_USERNAME") ?: "admin",
        password = System.getenv("ADMIN_PASSWORD") ?: "admin_password",
        email = "admin@foodies.com"
    )
}
```

### Utility Helpers

**File:** `e2e/src/main/kotlin/com/foodies/e2e/utils/HtmxHelpers.kt`

```kotlin
package com.foodies.e2e.utils

import com.microsoft.playwright.Page

object HtmxHelpers {
    /**
     * Wait for HTMX swap event to complete.
     * This ensures HTMX has finished updating the DOM.
     */
    fun waitForHtmxSwap(page: Page) {
        page.evaluate("""
            new Promise(resolve => {
                document.body.addEventListener('htmx:afterSwap', resolve, { once: true });
            })
        """)
    }

    /**
     * Wait for HTMX request to complete.
     * Useful for tracking when HTMX starts and finishes a request.
     */
    fun waitForHtmxRequest(page: Page) {
        page.evaluate("""
            new Promise(resolve => {
                document.body.addEventListener('htmx:afterRequest', resolve, { once: true });
            })
        """)
    }

    /**
     * Check if HTMX is currently processing a request.
     */
    fun isHtmxBusy(page: Page): Boolean {
        return page.evaluate("htmx.find('body').classList.contains('htmx-request')") as Boolean
    }
}
```

**File:** `e2e/src/main/kotlin/com/foodies/e2e/utils/ApiHelpers.kt`

```kotlin
package com.foodies.e2e.utils

import com.foodies.e2e.config.TestConfig
import com.foodies.e2e.fixtures.TestMenuItem
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

object ApiHelpers {
    private val client = HttpClient(CIO)

    suspend fun createMenuItemViaAPI(item: TestMenuItem): HttpResponse {
        return client.post("${TestConfig.menuServiceUrl}/menu") {
            contentType(ContentType.Application.Json)
            setBody(item)
        }
    }

    suspend fun clearBasketViaAPI(userId: String): HttpResponse {
        return client.delete("${TestConfig.webappBaseUrl}/basket/$userId")
    }

    suspend fun seedTestData() {
        // Seed database with test data
        // This can be called in test setup
    }

    fun close() {
        client.close()
    }
}
```

**File:** `e2e/src/main/kotlin/com/foodies/e2e/utils/TestHelpers.kt`

```kotlin
package com.foodies.e2e.utils

import com.microsoft.playwright.Page
import java.nio.file.Path

object TestHelpers {
    /**
     * Take a screenshot and save to the specified path.
     */
    fun takeScreenshot(page: Page, path: Path) {
        page.screenshot(Page.ScreenshotOptions().setPath(path))
    }

    /**
     * Wait for a specific network idle state.
     */
    fun waitForNetworkIdle(page: Page) {
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE)
    }

    /**
     * Scroll to element and wait for it to be visible.
     */
    fun scrollToElement(page: Page, selector: String) {
        page.locator(selector).scrollIntoViewIfNeeded()
    }
}
```

## CI/CD Integration

### GitHub Actions Workflow for HTMX Tests

**File:** `.github/workflows/e2e-htmx.yml`

```yaml
name: E2E Tests - HTMX Web App

on:
  push:
    branches: [main]
    paths:
      - 'webapp/**'
      - 'menu/**'
      - 'profile/**'
      - 'basket/**'
      - 'e2e/**'
  pull_request:
    branches: [main]

jobs:
  test:
    timeout-minutes: 60
    runs-on: ubuntu-latest

    services:
      postgres-profile:
        image: postgres:15
        env:
          POSTGRES_DB: foodies-profile
          POSTGRES_USER: profile_user
          POSTGRES_PASSWORD: profile_pass
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      postgres-menu:
        image: postgres:15
        env:
          POSTGRES_DB: foodies-menu
          POSTGRES_USER: menu_user
          POSTGRES_PASSWORD: menu_pass
        ports:
          - 5433:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      postgres-basket:
        image: postgres:15
        env:
          POSTGRES_DB: foodies-basket
          POSTGRES_USER: basket_user
          POSTGRES_PASSWORD: basket_pass
        ports:
          - 5434:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      rabbitmq:
        image: rabbitmq:3-management
        ports:
          - 5672:5672
          - 15672:15672
        env:
          RABBITMQ_DEFAULT_USER: foodies
          RABBITMQ_DEFAULT_PASS: foodies_password
        options: >-
          --health-cmd "rabbitmq-diagnostics -q ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      keycloak:
        image: foodies-keycloak:latest
        ports:
          - 8000:8080
        env:
          KEYCLOAK_ADMIN: admin
          KEYCLOAK_ADMIN_PASSWORD: admin
          KC_HTTP_ENABLED: true
          KC_HOSTNAME_STRICT: false
        options: >-
          --health-cmd "curl -f http://localhost:8080/health/ready || exit 1"
          --health-interval 30s
          --health-timeout 10s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Install Playwright Browsers
        run: ./gradlew :e2e:installPlaywrightBrowsers

      - name: Build Keycloak provider
        run: ./gradlew :keycloak-rabbitmq-publisher:build

      - name: Wait for Keycloak
        run: |
          timeout 300 bash -c 'until curl -f http://localhost:8000/health/ready; do sleep 5; done'

      - name: Run E2E tests
        run: ./gradlew :e2e:test
        env:
          CI: true
          WEBAPP_BASE_URL: http://foodies.local:8080
          MENU_SERVICE_URL: http://foodies.local:8082
          PROFILE_SERVICE_URL: http://foodies.local:8081
          TEST_USERNAME: food_lover
          TEST_PASSWORD: password
          KEYCLOAK_BASE_URL: http://foodies.local:8000
          DB_URL_PROFILE: jdbc:postgresql://localhost:5432/foodies-profile
          DB_URL_MENU: jdbc:postgresql://localhost:5433/foodies-menu
          DB_URL_BASKET: jdbc:postgresql://localhost:5434/foodies-basket
          REDIS_HOST: localhost
          REDIS_PORT: 6379
          RABBITMQ_HOST: localhost
          RABBITMQ_PORT: 5672

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: e2e-test-report
          path: e2e/build/reports/tests/
          retention-days: 30

      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: e2e-screenshots
          path: e2e/build/playwright/
          retention-days: 7
```

### GitHub Actions Workflow for KMP Tests

**File:** `.github/workflows/e2e-kmp.yml`

```yaml
name: E2E Tests - KMP Web/WASM

on:
  push:
    branches: [main]
    paths:
      - 'kmp/**'
      - 'e2e/**'
  pull_request:
    branches: [main]

jobs:
  test:
    timeout-minutes: 60
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Install Playwright Browsers
        run: ./gradlew :e2e:installPlaywrightBrowsers

      - name: Build KMP Web/WASM
        run: ./gradlew :kmp:wasmJsBrowserDistribution

      - name: Start KMP Web Server
        run: |
          cd kmp/build/dist/wasmJs/productionExecutable
          python3 -m http.server 8081 &
          echo $! > server.pid

      - name: Run KMP E2E tests
        run: ./gradlew :e2e:test --tests "com.foodies.e2e.kmp.*"
        env:
          CI: true
          KMP_WEB_BASE_URL: http://foodies.local:8081
          TEST_USERNAME: food_lover
          TEST_PASSWORD: password

      - name: Stop KMP Web Server
        if: always()
        run: |
          if [ -f kmp/build/dist/wasmJs/productionExecutable/server.pid ]; then
            kill $(cat kmp/build/dist/wasmJs/productionExecutable/server.pid)
          fi

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: kmp-e2e-test-report
          path: e2e/build/reports/tests/
          retention-days: 30
```

## Best Practices

### 1. Test Isolation

- Each test should be independent and not rely on other tests
- Use setup/teardown hooks to prepare test state
- Clean up test data after each test
- Use separate storage states for different test scenarios

### 2. Waiting Strategies

- Prefer explicit waits with standard assertions: `assertTrue(locator.isVisible(), "message")`
- Use Playwright's auto-waiting mechanisms
- For HTMX: Use `HtmxHelpers.waitForHtmxSwap(page)` to wait for DOM updates
- Avoid hard-coded delays except for animations or known timing issues

### 3. Selectors

**Priority order:**
1. Accessible roles: `page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Submit"))`
2. Labels: `page.getByLabel("Username")`
3. Test IDs: `page.locator("[data-testid='menu-item']")`
4. Text content: `page.getByText("Welcome")`
5. CSS selectors (last resort): `page.locator(".menu-item")`

### 4. HTMX-Specific Considerations

- Wait for HTMX swaps to complete using `HtmxHelpers.waitForHtmxSwap(page)`
- Test Out-of-Band (OOB) swaps
- Verify history state changes
- Test infinite scroll trigger zones
- Validate proper loading states
- Check `HtmxHelpers.isHtmxBusy(page)` for request state

Example usage (already available in `HtmxHelpers.kt`):

```kotlin
// Wait for HTMX swap
HtmxHelpers.waitForHtmxSwap(page)

// Wait for HTMX request to complete
HtmxHelpers.waitForHtmxRequest(page)

// Check if HTMX is currently processing
val isBusy = HtmxHelpers.isHtmxBusy(page)
```

### 5. Performance Testing

Consider adding performance assertions:

```kotlin
test("Performance - should load menu page within 2 seconds") {
    val start = System.currentTimeMillis()
    page().navigate("/menu")
    val loadTime = System.currentTimeMillis() - start

    assertTrue(loadTime < 2000, "Page should load within 2 seconds (actual: ${loadTime}ms)")
}
```

### 6. Visual Regression Testing

Optional: Add visual comparison tests using Playwright's screenshot comparison:

```kotlin
test("Visual Regression - should match menu page screenshot") {
    page().navigate("/menu")
    page().screenshot(Page.ScreenshotOptions().setPath(Paths.get("screenshots/menu-page.png")))
    // Compare with baseline screenshot
}
```

## Running Tests

### Local Development

```bash
# Install Playwright browsers (first time only)
./gradlew :e2e:installPlaywrightBrowsers

# Run all E2E tests
./gradlew :e2e:test

# Run specific test class
./gradlew :e2e:test --tests "com.foodies.e2e.htmx.basket.AddToBasketTest"

# Run all HTMX tests
./gradlew :e2e:test --tests "com.foodies.e2e.htmx.*"

# Run all KMP tests
./gradlew :e2e:test --tests "com.foodies.e2e.kmp.*"

# Run tests with specific browser in non-headless mode using environment variables
HEADLESS=false ./gradlew :e2e:test

# Run tests with slow motion (for debugging)
SLOW_MO=1000 ./gradlew :e2e:test

# Run tests with custom configuration via command-line args
./gradlew :e2e:test --args="--headless=false --webappBaseUrl=http://localhost:8080"

# Run tests with custom configuration for CI
./gradlew :e2e:test --args="--webappBaseUrl=http://foodies.local:8080 --ci=true"

# View test report
open e2e/build/reports/tests/test/index.html
```

### Debugging Tests

For debugging Playwright tests in Kotlin:
1. Set breakpoints in your IDE (IntelliJ IDEA)
2. Run tests in debug mode from the IDE
3. Use `HEADLESS=false` to see the browser
4. Use `SLOW_MO=500` to slow down test execution
5. Take screenshots using `TestHelpers.takeScreenshot(page, path)`

### CI Environment

Tests run automatically on:
- Push to `main` branch
- Pull requests to `main`
- Manual workflow dispatch

All tests run in headless mode with full service dependencies (PostgreSQL, Redis, RabbitMQ, Keycloak).

## Maintenance and Troubleshooting

### Common Issues

1. **Flaky Tests**
   - Increase timeout in `TestConfig.kt` for slow operations
   - Use proper wait conditions with `HtmxHelpers.waitForHtmxSwap(page)`
   - Check for race conditions in HTMX swaps
   - Ensure Playwright's auto-waiting is not being bypassed

2. **Authentication Failures**
   - Verify Keycloak is running and configured at `foodies.local:8000`
   - Check environment variables (`TEST_USERNAME`, `TEST_PASSWORD`)
   - Ensure storage state directory exists and is writable
   - Run `AuthSetup` test first to create authentication state

3. **Selector Not Found**
   - Run tests with `HEADLESS=false` to see the browser
   - Use `TestHelpers.takeScreenshot(page, path)` to capture page state
   - Check if element is in shadow DOM (unlikely with HTMX/Compose)
   - Verify element is visible before interaction with `locator.isVisible()`

4. **Timeout Errors**
   - Increase timeout in `TestConfig.kt` (`defaultTimeout`, `navigationTimeout`)
   - Check if all services are running (`./gradlew :webapp:run`, etc.)
   - Verify `/etc/hosts` has `foodies.local` pointing to `127.0.0.1`
   - Check network connectivity

5. **Playwright Browser Not Found**
   - Run `./gradlew :e2e:installPlaywrightBrowsers` to install browsers
   - Ensure sufficient disk space for browser binaries
   - Check Playwright version compatibility

### Debugging

```bash
# Run tests in headed mode with slow motion
HEADLESS=false SLOW_MO=500 ./gradlew :e2e:test

# Run specific test with IntelliJ debugger
# 1. Open test file in IntelliJ
# 2. Set breakpoints
# 3. Right-click test -> Debug

# Take screenshots during test execution
// In your test code:
TestHelpers.takeScreenshot(page, Paths.get("debug-screenshot.png"))

# Check Playwright logs
# Playwright Java API logs to stdout, check Gradle test output
```

## Future Enhancements

1. **API Contract Testing**: Add Pact or similar for contract testing between services
2. **Load Testing**: Integrate k6 or Artillery for performance testing
3. **Accessibility Testing**: Add axe-core for a11y audits
4. **Mobile Native Testing**: Set up Appium/Maestro for Android/iOS
5. **Cross-Browser Testing**: Enable Firefox and WebKit in CI
6. **Visual Regression**: Set up Percy or similar for visual comparisons
7. **Test Data Seeding**: Automate test data setup via scripts
8. **Monitoring**: Integrate test results with monitoring tools

## References

- [Playwright Java API Documentation](https://playwright.dev/java/)
- [Playwright Java GitHub](https://github.com/microsoft/playwright-java)
- [Kotest Documentation](https://kotest.io/)
- [eShop Reference Implementation](https://github.com/dotnet/eShop)
- [HTMX Testing Guide](https://htmx.org/docs/#testing)
- [Kotlin Multiplatform Compose](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Gradle Test Documentation](https://docs.gradle.org/current/userguide/java_testing.html)

## Conclusion

This specification provides a comprehensive e2e testing strategy for the Foodies project, covering both HTMX and KMP implementations. The approach leverages Playwright's Java API with Kotlin and TestBalloon, following patterns proven in the eShop reference architecture, adapted for the Kotlin/Ktor ecosystem.

Key benefits:
- ✅ **Single Language Stack** - Write tests in Kotlin, same as the application
- ✅ **Type Safety** - Leverage Kotlin's type system for test code and fixtures
- ✅ **Consistent Testing Framework** - Use TestBalloon throughout the entire project
- ✅ **Type-safe Fixture Management** - TestSuite.Fixture<T> with automatic cleanup
- ✅ **Configurable Tests** - Command-line args and environment variable support
- ✅ **Automated authentication flow** - Reusable authentication state
- ✅ **Parallel test execution** - Fast feedback with TestBalloon's parallel execution
- ✅ **Rich reporting and debugging** - Gradle test reports + Playwright traces
- ✅ **CI/CD integration** - Seamless integration with existing Gradle workflows
- ✅ **Support for multiple UI implementations** - HTMX and KMP Web/WASM
- ✅ **Maintainable test structure** - Clear separation and organization
- ✅ **Production-ready patterns** - Based on proven eShop patterns
- ✅ **HTMX-specific helpers** - Custom utilities for HTMX event handling
- ✅ **No global mutable state** - All resources managed via fixtures
