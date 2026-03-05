# Plan: Webapp Feature-Based Package Restructure

> Source PRD: `webapp/PRD.md`

## Architectural decisions

- **Package root**: `io.ktor.foodies.server`
- **Feature packages**: `cart/`, `menu/`, `home/`, `security/`, `shared/` — all at the top level, no `htmx/` wrapper
- **Module pattern**: each feature exposes a `<Feature>Module.kt` with a `<feature>Module()` function; `WebAppModule` is a thin orchestrator that calls these
- **Shared HTMX helpers**: live in `shared/HtmxSyntax.kt`
- **No behavior changes**: routes, URL patterns, HTMX fragments, and HTTP API contracts are unchanged

---

## Phase 1: Shared infrastructure

**User stories**: Establish the shared foundation all features depend on

### What to build

Move `htmx/Syntax.kt` to `shared/HtmxSyntax.kt` and extract Redis/session wiring from `WebAppModule` into a new `security/SecurityModule.kt`. No behavior changes — this is purely structural relocation.

### Acceptance criteria

- [x] `shared/HtmxSyntax.kt` exists with updated package declaration; `htmx/Syntax.kt` is deleted
- [x] `security/SecurityModule.kt` encapsulates Redis client and session storage wiring
- [x] `WebAppModule` delegates security setup to `securityModule()`
- [x] All existing tests pass

---

## Phase 2: Migrate `menu/` feature

**User stories**: Consolidate menu domain into a self-contained feature package

### What to build

Move `htmx/menu/MenuRoutes.kt` and `htmx/menu/MenuService.kt` to a top-level `menu/` package. Split the `MenuService` interface from its `HttpMenuService` implementation. Create `MenuModule.kt` to own the wiring. Update `WebAppModule` to call `menuModule()`.

### Acceptance criteria

- [x] `menu/MenuService.kt` (interface), `menu/HttpMenuService.kt` (impl), `menu/MenuRoutes.kt`, and `menu/MenuModule.kt` all exist with correct package declarations
- [x] `htmx/menu/` is deleted
- [x] `WebAppModule` calls `menuModule()` instead of wiring menu directly
- [x] All existing tests pass

---

## Phase 3: Migrate `cart/` feature

**User stories**: Consolidate the split basket/cart domain into a single `cart/` feature package

### What to build

Merge `htmx/basket/` (service + domain) and `htmx/cart/` (routes) into a single top-level `cart/` package. Rename `BasketService` → `CartService`, `HttpBasketService` → `HttpCartService`, `Domain.kt` → `CartDomain.kt`. Create `CartModule.kt` to own the wiring. Update `WebAppModule` to call `cartModule()`.

### Acceptance criteria

- [x] `cart/CartService.kt`, `cart/HttpCartService.kt`, `cart/CartDomain.kt`, `cart/CartRoutes.kt`, and `cart/CartModule.kt` all exist with correct package declarations
- [x] `htmx/basket/` and `htmx/cart/` are deleted
- [x] `WebAppModule` calls `cartModule()` instead of wiring cart/basket directly
- [x] All existing tests pass

---

## Phase 4: Migrate `home/` feature

**User stories**: Give the home page a proper feature package

### What to build

Move `htmx/Home.kt` to `home/HomeRoutes.kt` and create `home/HomeModule.kt` to wire the home route. Update `WebAppModule` to call `homeModule()`.

### Acceptance criteria

- [x] `home/HomeRoutes.kt` and `home/HomeModule.kt` exist with correct package declarations
- [x] `htmx/Home.kt` is deleted
- [x] `WebAppModule` calls `homeModule()`
- [x] All existing tests pass

---

## Phase 5: Finalize `WebAppModule`, delete `htmx/`, update tests

**User stories**: Complete the restructure and verify end-to-end correctness

### What to build

Slim `WebAppModule` down to a pure orchestrator (no direct service/client construction). Update `WebApp.kt` to remove any remaining `htmx.*` references. Delete the now-empty `htmx/` directory. Mirror the test tree: move `session/RedisSessionStorageSpec.kt`, `session/UserSessionScopeSpec.kt`, and `session/TestSyntax.kt` into `security/`.

### Acceptance criteria

- [x] `htmx/` directory no longer exists anywhere in the source tree
- [x] `WebAppModule` contains no direct service or HTTP client construction
- [x] `WebApp.kt` has no imports from `htmx.*`
- [x] Test files live under `security/` matching the new main source layout
- [x] `./gradlew :webapp:jvmTest` passes with no failures
