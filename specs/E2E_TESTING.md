# End-to-End Testing Specification for Foodies

## Overview

This specification defines the end-to-end (e2e) testing strategy for the Foodies project, based on the patterns established in the eShop reference project. The Foodies project has two distinct UI implementations that require e2e testing:

1. **HTMX-based Web Application** (Ktor server-side rendered)
2. **Kotlin Multiplatform Compose** (KMP: Android, iOS, Desktop, Web/WASM)

## Testing Framework

### Primary Framework: Playwright

**Rationale:** Playwright provides:
- Modern browser automation with excellent developer experience
- Built-in authentication state management
- Parallel test execution
- Rich reporting and tracing capabilities
- Cross-browser support (Chromium, Firefox, WebKit)
- Automatic waiting and retry mechanisms
- Mobile viewport emulation for responsive testing

**Version:** Latest stable (v1.42.1+)

**Installation:**
```bash
npm init playwright@latest
```

## Project Structure

```
foodies/
├── e2e/
│   ├── htmx/                          # HTMX webapp tests
│   │   ├── setup/
│   │   │   └── auth.setup.ts         # Authentication setup
│   │   ├── menu/
│   │   │   ├── browse-menu.spec.ts   # Browse menu (unauthenticated)
│   │   │   └── menu-pagination.spec.ts # Test infinite scroll
│   │   ├── basket/
│   │   │   ├── add-to-basket.spec.ts # Add items to basket
│   │   │   ├── update-basket.spec.ts # Update quantities
│   │   │   └── remove-from-basket.spec.ts # Remove items
│   │   ├── order/
│   │   │   ├── create-order.spec.ts  # Place order flow
│   │   │   └── order-history.spec.ts # View past orders
│   │   ├── profile/
│   │   │   └── view-profile.spec.ts  # Profile management
│   │   └── auth/
│   │       ├── login.spec.ts         # OAuth login flow
│   │       └── logout.spec.ts        # Logout flow
│   ├── kmp/                           # Kotlin Multiplatform Compose tests
│   │   ├── setup/
│   │   │   └── auth.setup.ts         # Authentication setup
│   │   ├── web/                       # Web/WASM specific tests
│   │   │   └── menu-navigation.spec.ts
│   │   ├── android/                   # Android specific tests (future)
│   │   └── ios/                       # iOS specific tests (future)
│   └── shared/
│       ├── fixtures/
│       │   ├── menu-data.ts          # Test data for menu items
│       │   └── user-data.ts          # Test user credentials
│       └── utils/
│           ├── api-helpers.ts        # API interaction helpers
│           └── test-helpers.ts       # Common test utilities
├── playwright.config.ts               # Main Playwright configuration
├── playwright.htmx.config.ts         # HTMX-specific configuration
├── playwright.kmp.config.ts          # KMP-specific configuration
├── package.json
└── .github/
    └── workflows/
        ├── e2e-htmx.yml              # CI for HTMX tests
        └── e2e-kmp.yml               # CI for KMP tests
```

## Configuration

### Base Configuration (`playwright.config.ts`)

```typescript
import { defineConfig, devices } from '@playwright/test';
import * as path from 'path';

export const STORAGE_STATE = path.join(__dirname, 'playwright/.auth/user.json');

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',

  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    // HTMX Web App Tests
    {
      name: 'htmx-setup',
      testMatch: /.*\.setup\.ts/,
      testDir: './e2e/htmx/setup',
    },
    {
      name: 'htmx-authenticated',
      testDir: './e2e/htmx',
      testIgnore: ['**/setup/**', '**/browse-menu.spec.ts'],
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.WEBAPP_BASE_URL || 'http://localhost:8080',
        storageState: STORAGE_STATE,
      },
      dependencies: ['htmx-setup'],
    },
    {
      name: 'htmx-unauthenticated',
      testMatch: ['**/browse-menu.spec.ts'],
      testDir: './e2e/htmx',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.WEBAPP_BASE_URL || 'http://localhost:8080',
      },
    },

    // KMP Web/WASM Tests
    {
      name: 'kmp-web-setup',
      testMatch: /.*\.setup\.ts/,
      testDir: './e2e/kmp/setup',
    },
    {
      name: 'kmp-web',
      testDir: './e2e/kmp/web',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.KMP_WEB_BASE_URL || 'http://localhost:8081',
        storageState: STORAGE_STATE,
      },
      dependencies: ['kmp-web-setup'],
    },

    // Mobile viewports for responsive testing
    {
      name: 'htmx-mobile',
      testDir: './e2e/htmx',
      testIgnore: ['**/setup/**'],
      use: {
        ...devices['Pixel 5'],
        baseURL: process.env.WEBAPP_BASE_URL || 'http://localhost:8080',
      },
      dependencies: ['htmx-setup'],
    },
  ],

  // Web server configuration
  webServer: process.env.CI ? undefined : [
    {
      command: './gradlew :webapp:run',
      url: 'http://localhost:8080/healthz',
      timeout: 120 * 1000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: './gradlew :menu:run',
      url: 'http://localhost:8082/healthz',
      timeout: 120 * 1000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: './gradlew :profile:run',
      url: 'http://localhost:8081/healthz',
      timeout: 120 * 1000,
      reuseExistingServer: !process.env.CI,
    },
  ],
});
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

**File:** `e2e/htmx/setup/auth.setup.ts`

```typescript
import { test as setup, expect } from '@playwright/test';
import { STORAGE_STATE } from '../../../playwright.config';

setup('authenticate with Keycloak', async ({ page }) => {
  // Navigate to home page
  await page.goto('/');

  // Click login button
  await page.getByLabel('Sign in').click();

  // Should redirect to Keycloak
  await expect(page).toHaveURL(/.*keycloak.*/);

  // Fill login form
  await page.getByLabel('Username').fill(process.env.TEST_USERNAME!);
  await page.getByLabel('Password').fill(process.env.TEST_PASSWORD!);
  await page.getByRole('button', { name: 'Sign In' }).click();

  // Should redirect back to home page
  await expect(page).toHaveURL('/');
  await expect(page.getByText('Welcome back')).toBeVisible();

  // Save authentication state
  await page.context().storageState({ path: STORAGE_STATE });
});
```

**File:** `e2e/htmx/auth/logout.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('logout flow', async ({ page }) => {
  await page.goto('/');

  // Click logout
  await page.getByRole('button', { name: 'Logout' }).click();

  // Should redirect to Keycloak logout
  await expect(page).toHaveURL(/.*\/logout.*/);

  // Navigate back to home
  await page.goto('/');

  // Should show login button
  await expect(page.getByLabel('Sign in')).toBeVisible();
});
```

#### 1.2 Menu Browsing Tests

**File:** `e2e/htmx/menu/browse-menu.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('browse menu without authentication', async ({ page }) => {
  await page.goto('/');

  // Should see menu items
  await expect(page.getByRole('heading', { name: 'Menu' })).toBeVisible();

  // Should see at least one menu item
  const menuItems = page.locator('[data-testid="menu-item"]');
  await expect(menuItems).toHaveCountGreaterThan(0);

  // Click on first item to view details
  await menuItems.first().click();

  // Should show item details
  await expect(page.locator('[data-testid="item-details"]')).toBeVisible();
});
```

**File:** `e2e/htmx/menu/menu-pagination.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('infinite scroll loads more items', async ({ page }) => {
  await page.goto('/');

  // Get initial item count
  const initialItems = await page.locator('[data-testid="menu-item"]').count();

  // Scroll to bottom to trigger HTMX load
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));

  // Wait for HTMX to load more items
  await page.waitForTimeout(1000);

  // Should have more items
  const updatedItems = await page.locator('[data-testid="menu-item"]').count();
  expect(updatedItems).toBeGreaterThan(initialItems);
});
```

#### 1.3 Basket Tests

**File:** `e2e/htmx/basket/add-to-basket.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('add item to basket', async ({ page }) => {
  await page.goto('/');

  // Click on a menu item
  await page.locator('[data-testid="menu-item"]').first().click();

  // Add to basket
  await page.getByRole('button', { name: 'Add to Basket' }).click();

  // Should show success message (HTMX swap)
  await expect(page.getByText('Added to basket')).toBeVisible();

  // Basket count should update
  await expect(page.locator('[data-testid="basket-count"]')).toHaveText('1');

  // Navigate to basket
  await page.getByRole('link', { name: 'Basket' }).click();

  // Should see item in basket
  const basketItems = page.locator('[data-testid="basket-item"]');
  await expect(basketItems).toHaveCount(1);
});
```

**File:** `e2e/htmx/basket/update-basket.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('update basket item quantity', async ({ page }) => {
  // Setup: Add item to basket first
  await page.goto('/');
  await page.locator('[data-testid="menu-item"]').first().click();
  await page.getByRole('button', { name: 'Add to Basket' }).click();

  // Navigate to basket
  await page.getByRole('link', { name: 'Basket' }).click();

  // Update quantity
  const quantityInput = page.locator('[data-testid="quantity-input"]').first();
  await quantityInput.fill('3');
  await page.getByRole('button', { name: 'Update' }).click();

  // Should update via HTMX
  await expect(quantityInput).toHaveValue('3');

  // Total should update
  await expect(page.locator('[data-testid="basket-total"]')).not.toBeEmpty();
});
```

**File:** `e2e/htmx/basket/remove-from-basket.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('remove item from basket', async ({ page }) => {
  // Setup: Add item to basket
  await page.goto('/');
  await page.locator('[data-testid="menu-item"]').first().click();
  await page.getByRole('button', { name: 'Add to Basket' }).click();

  // Navigate to basket
  await page.getByRole('link', { name: 'Basket' }).click();

  // Get initial count
  const initialCount = await page.locator('[data-testid="basket-item"]').count();

  // Remove item
  await page.locator('[data-testid="remove-button"]').first().click();

  // Should remove via HTMX
  await expect(page.locator('[data-testid="basket-item"]')).toHaveCount(initialCount - 1);

  // If last item, should show empty state
  if (initialCount === 1) {
    await expect(page.getByText('Your basket is empty')).toBeVisible();
  }
});
```

#### 1.4 Order Tests

**File:** `e2e/htmx/order/create-order.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('complete order flow', async ({ page }) => {
  // Setup: Add items to basket
  await page.goto('/');
  await page.locator('[data-testid="menu-item"]').first().click();
  await page.getByRole('button', { name: 'Add to Basket' }).click();

  // Navigate to basket
  await page.getByRole('link', { name: 'Basket' }).click();

  // Proceed to checkout
  await page.getByRole('button', { name: 'Checkout' }).click();

  // Should show order confirmation
  await expect(page.getByRole('heading', { name: 'Order Confirmation' })).toBeVisible();

  // Confirm order
  await page.getByRole('button', { name: 'Place Order' }).click();

  // Should show success message
  await expect(page.getByText('Order placed successfully')).toBeVisible();

  // Should show order number
  await expect(page.locator('[data-testid="order-number"]')).toBeVisible();
});
```

**File:** `e2e/htmx/order/order-history.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('view order history', async ({ page }) => {
  await page.goto('/orders');

  // Should show orders heading
  await expect(page.getByRole('heading', { name: 'My Orders' })).toBeVisible();

  // Should show order list (may be empty)
  const ordersList = page.locator('[data-testid="orders-list"]');
  await expect(ordersList).toBeVisible();

  // If orders exist, click on first order
  const orders = page.locator('[data-testid="order-item"]');
  const count = await orders.count();

  if (count > 0) {
    await orders.first().click();

    // Should show order details
    await expect(page.locator('[data-testid="order-details"]')).toBeVisible();
  }
});
```

#### 1.5 Profile Tests

**File:** `e2e/htmx/profile/view-profile.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('view user profile', async ({ page }) => {
  await page.goto('/profile');

  // Should show profile information
  await expect(page.getByRole('heading', { name: 'Profile' })).toBeVisible();

  // Should display user information
  await expect(page.locator('[data-testid="user-email"]')).toBeVisible();
  await expect(page.locator('[data-testid="user-name"]')).toBeVisible();
});
```

### 2. Kotlin Multiplatform Compose Tests

#### 2.1 Web/WASM Tests

**File:** `e2e/kmp/setup/auth.setup.ts`

```typescript
import { test as setup, expect } from '@playwright/test';
import { STORAGE_STATE } from '../../../playwright.config';

setup('authenticate KMP web app', async ({ page }) => {
  await page.goto('/');

  // KMP Compose Web authentication flow
  await page.getByRole('button', { name: 'Sign In' }).click();

  // Fill credentials (adjust selectors based on Compose Web rendering)
  await page.getByLabel('Username').fill(process.env.TEST_USERNAME!);
  await page.getByLabel('Password').fill(process.env.TEST_PASSWORD!);
  await page.getByRole('button', { name: 'Login' }).click();

  // Wait for successful authentication
  await expect(page.getByRole('button', { name: 'Profile' })).toBeVisible();

  await page.context().storageState({ path: STORAGE_STATE });
});
```

**File:** `e2e/kmp/web/menu-navigation.spec.ts`

```typescript
import { test, expect } from '@playwright/test';

test('navigate through menu in KMP web app', async ({ page }) => {
  await page.goto('/');

  // Should render Compose Web content
  await expect(page.locator('canvas')).toBeVisible(); // Compose Web uses canvas

  // Test navigation (adjust based on actual Compose Web implementation)
  // Note: Compose Web may require different interaction patterns

  // Click menu tab/button
  await page.getByRole('button', { name: 'Menu' }).click();

  // Should show menu items
  await expect(page.locator('[data-testid="menu-list"]')).toBeVisible();
});
```

**Note:** KMP Compose Web/WASM tests may require special handling due to how Compose renders to canvas. Consider:
- Using accessibility selectors where possible
- Testing via API calls for complex interactions
- Screenshot comparison for UI validation
- Custom test helpers for Compose-specific interactions

#### 2.2 Android/iOS Tests (Future)

For native mobile platforms, consider:
- **Appium** for native app testing
- **Maestro** for mobile UI testing
- **Compose UI Testing** for Android (JVM-based)
- **XCUITest** for iOS

These will require separate configuration and test runners.

## Test Data Management

### Fixtures

**File:** `e2e/shared/fixtures/menu-data.ts`

```typescript
export const testMenuItems = [
  {
    name: 'Margherita Pizza',
    description: 'Classic tomato and mozzarella pizza',
    price: 12.99,
    imageUrl: 'https://example.com/pizza.jpg',
  },
  {
    name: 'Caesar Salad',
    description: 'Fresh romaine with parmesan and croutons',
    price: 8.99,
    imageUrl: 'https://example.com/salad.jpg',
  },
  // Add more test data as needed
];
```

**File:** `e2e/shared/fixtures/user-data.ts`

```typescript
export const testUsers = {
  regular: {
    username: process.env.TEST_USERNAME || 'food_lover',
    password: process.env.TEST_PASSWORD || 'password',
    email: 'food_lover@gmail.com',
  },
  admin: {
    username: process.env.ADMIN_USERNAME || 'admin',
    password: process.env.ADMIN_PASSWORD || 'admin_password',
    email: 'admin@foodies.com',
  },
};
```

### API Helpers

**File:** `e2e/shared/utils/api-helpers.ts`

```typescript
import { request } from '@playwright/test';

export async function createMenuItemViaAPI(item: {
  name: string;
  description: string;
  price: number;
  imageUrl: string;
}) {
  const context = await request.newContext({
    baseURL: process.env.MENU_SERVICE_URL || 'http://localhost:8082',
  });

  const response = await context.post('/menu', {
    data: item,
  });

  return response.json();
}

export async function clearBasketViaAPI(userId: string) {
  const context = await request.newContext({
    baseURL: process.env.WEBAPP_BASE_URL || 'http://localhost:8080',
  });

  await context.delete(`/basket/${userId}`);
}

export async function seedTestData() {
  // Seed database with test data
  // This can be called in global setup
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
      - 'e2e/htmx/**'
      - 'e2e/shared/**'
      - 'playwright.config.ts'
      - 'playwright.htmx.config.ts'
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

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: 'lts/*'

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright Browsers
        run: npx playwright install chromium

      - name: Build Keycloak provider
        run: ./gradlew :keycloak-rabbitmq-publisher:build

      - name: Wait for Keycloak
        run: |
          timeout 300 bash -c 'until curl -f http://localhost:8000/health/ready; do sleep 5; done'

      - name: Run Playwright tests
        run: npx playwright test --config=playwright.htmx.config.ts
        env:
          CI: true
          WEBAPP_BASE_URL: http://localhost:8080
          MENU_SERVICE_URL: http://localhost:8082
          PROFILE_SERVICE_URL: http://localhost:8081
          TEST_USERNAME: food_lover
          TEST_PASSWORD: password
          KEYCLOAK_BASE_URL: http://localhost:8000
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
          name: playwright-report-htmx
          path: playwright-report/
          retention-days: 30

      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: playwright-screenshots
          path: test-results/
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
      - 'e2e/kmp/**'
      - 'e2e/shared/**'
      - 'playwright.config.ts'
      - 'playwright.kmp.config.ts'
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

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: 'lts/*'

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright Browsers
        run: npx playwright install chromium

      - name: Build KMP Web/WASM
        run: ./gradlew :kmp:wasmJsBrowserDistribution

      - name: Start KMP Web Server
        run: |
          cd kmp/build/dist/wasmJs/productionExecutable
          python3 -m http.server 8081 &
          echo $! > server.pid

      - name: Run Playwright tests
        run: npx playwright test --config=playwright.kmp.config.ts
        env:
          CI: true
          KMP_WEB_BASE_URL: http://localhost:8081
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
          name: playwright-report-kmp
          path: playwright-report/
          retention-days: 30
```

## Best Practices

### 1. Test Isolation

- Each test should be independent and not rely on other tests
- Use setup/teardown hooks to prepare test state
- Clean up test data after each test
- Use separate storage states for different test scenarios

### 2. Waiting Strategies

- Prefer explicit waits: `await expect(locator).toBeVisible()`
- Avoid hard-coded `waitForTimeout()` except for animations
- Use Playwright's auto-waiting mechanisms
- For HTMX: wait for `htmx:afterSwap` events

### 3. Selectors

**Priority order:**
1. Accessible roles: `getByRole('button', { name: 'Submit' })`
2. Labels: `getByLabel('Username')`
3. Test IDs: `locator('[data-testid="menu-item"]')`
4. Text content: `getByText('Welcome')`
5. CSS selectors (last resort): `locator('.menu-item')`

### 4. HTMX-Specific Considerations

- Wait for HTMX swaps to complete
- Test Out-of-Band (OOB) swaps
- Verify history state changes
- Test infinite scroll trigger zones
- Validate proper loading states

Example HTMX wait helper:

```typescript
async function waitForHtmxSwap(page: Page) {
  await page.evaluate(() => {
    return new Promise(resolve => {
      document.body.addEventListener('htmx:afterSwap', resolve, { once: true });
    });
  });
}
```

### 5. Performance Testing

Consider adding performance assertions:

```typescript
test('menu page loads within 2 seconds', async ({ page }) => {
  const start = Date.now();
  await page.goto('/menu');
  const loadTime = Date.now() - start;

  expect(loadTime).toBeLessThan(2000);
});
```

### 6. Visual Regression Testing

Optional: Add visual comparison tests using Playwright's screenshot comparison:

```typescript
test('menu page visual regression', async ({ page }) => {
  await page.goto('/menu');
  await expect(page).toHaveScreenshot('menu-page.png');
});
```

## Running Tests

### Local Development

```bash
# Install dependencies
npm ci

# Run all HTMX tests
npx playwright test --config=playwright.htmx.config.ts

# Run specific test file
npx playwright test e2e/htmx/basket/add-to-basket.spec.ts

# Run tests in headed mode (see browser)
npx playwright test --headed

# Run tests in debug mode
npx playwright test --debug

# Run tests for specific project
npx playwright test --project=htmx-authenticated

# View HTML report
npx playwright show-report
```

### CI Environment

Tests run automatically on:
- Push to `main` branch
- Pull requests to `main`
- Manual workflow dispatch

## Maintenance and Troubleshooting

### Common Issues

1. **Flaky Tests**
   - Increase timeout for slow operations
   - Use proper wait conditions
   - Check for race conditions in HTMX swaps

2. **Authentication Failures**
   - Verify Keycloak is running and configured
   - Check environment variables
   - Ensure storage state is saved correctly

3. **Selector Not Found**
   - Use Playwright Inspector: `npx playwright test --debug`
   - Check if element is in shadow DOM (unlikely with HTMX/Compose)
   - Verify element is visible before interaction

4. **Timeout Errors**
   - Increase timeout in configuration
   - Check if services are running
   - Verify network connectivity

### Debugging

```bash
# Run with trace on
npx playwright test --trace on

# View trace
npx playwright show-trace trace.zip

# Generate code from browser interactions
npx playwright codegen http://localhost:8080
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

- [Playwright Documentation](https://playwright.dev/)
- [eShop Reference Implementation](https://github.com/dotnet/eShop)
- [HTMX Testing Guide](https://htmx.org/docs/#testing)
- [Kotlin Multiplatform Compose](https://www.jetbrains.com/lp/compose-multiplatform/)
- [TestBalloon Framework](https://github.com/kotest/kotest)

## Conclusion

This specification provides a comprehensive e2e testing strategy for the Foodies project, covering both HTMX and KMP implementations. The approach leverages Playwright's modern features and follows patterns proven in the eShop reference architecture, adapted for Kotlin/Ktor ecosystem.

Key benefits:
- ✅ Automated authentication flow
- ✅ Parallel test execution
- ✅ Rich reporting and debugging
- ✅ CI/CD integration
- ✅ Support for multiple UI implementations
- ✅ Maintainable test structure
- ✅ Production-ready patterns
