# HTMX Web App E2E Testing Implementation

## Infrastructure & Setup
- [x] Add Playwright and Arrow dependencies to `gradle/libs.versions.toml`
- [x] Create `e2e` module directory structure
- [x] Configure `e2e/build.gradle.kts` with Playwright and TestBalloon
- [x] Add `installPlaywrightBrowsers` task to `e2e/build.gradle.kts`
- [x] Include `:e2e` module in `settings.gradle.kts`

## Configuration & Fixtures
- [x] Implement `com.foodies.e2e.config.TestConfig` for environment-based settings
- [x] Implement `com.foodies.e2e.config.PlaywrightFixtures` for Playwright resource management
- [x] Implement `com.foodies.e2e.fixtures.MenuData` with test menu items
- [x] Implement `com.foodies.e2e.fixtures.UserData` with test user credentials

## Utility Helpers
- [x] Implement `com.foodies.e2e.utils.HtmxHelpers` for waiting on HTMX swaps
- [x] Implement `com.foodies.e2e.utils.ApiHelpers` for backend interactions
- [x] Implement `com.foodies.e2e.utils.TestHelpers` for screenshots and scrolling

## Authentication Tests
- [x] Implement `com.foodies.e2e.setup.AuthSetup` to capture login session state
- [x] Implement `com.foodies.e2e.htmx.auth.LogoutTest` to verify logout flow

## Menu Tests
- [x] Implement `com.foodies.e2e.htmx.menu.BrowseMenuTest` for menu visibility and details
- [x] Implement `com.foodies.e2e.htmx.menu.MenuPaginationTest` for infinite scroll validation

## Basket Tests
- [x] Implement `com.foodies.e2e.htmx.basket.AddToBasketTest` for adding items
- [x] Implement `com.foodies.e2e.htmx.basket.UpdateBasketTest` for quantity changes
- [x] Implement `com.foodies.e2e.htmx.basket.RemoveFromBasketTest` for item removal

## Order & Profile Tests
- [ ] Implement `com.foodies.e2e.htmx.order.CreateOrderTest` for complete checkout flow
- [ ] Implement `com.foodies.e2e.htmx.order.OrderHistoryTest` for viewing past orders
- [x] Implement `com.foodies.e2e.htmx.profile.ViewProfileTest` for profile verification

## CI/CD Integration
- [ ] Create `.github/workflows/e2e-htmx.yml` to run E2E tests in GitHub Actions
