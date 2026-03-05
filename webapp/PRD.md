# PRD: Refactor `webapp` to Feature-Based Package Structure

## Goal

Reorganize the `webapp` module to follow the domain-feature package structure defined in `/ktor-service` conventions.
Replace the current `htmx/` technical-layer wrapper with top-level feature packages, each owning its full vertical slice.

---

## Current Structure

```
io.ktor.foodies.server/
├── WebApp.kt
├── WebAppModule.kt
├── Config.kt
├── security/
│   ├── JWT.kt
│   ├── RedisSessionStorage.kt
│   ├── Security.kt
│   └── UserSessionScope.kt
└── htmx/
    ├── Home.kt
    ├── Syntax.kt
    ├── basket/
    │   ├── BasketService.kt       # interface + HttpBasketService impl
    │   └── Domain.kt              # CustomerBasket, BasketItem, request DTOs
    ├── cart/
    │   └── CartRoutes.kt          # routes + all HTML fragments
    └── menu/
        ├── MenuRoutes.kt          # routes + all HTML fragments
        └── MenuService.kt         # interface + HttpMenuService impl
```

**Problems:**
- `htmx/` is a technical layer, not a domain concept.
- The cart domain is split across two packages: `basket/` (service + domain) and `cart/` (routes).
- `WebAppModule.kt` wires all features directly — no per-feature module functions.
- `Home.kt` and `Syntax.kt` are loose files inside `htmx/` with no clear ownership.

---

## Target Structure

```
io.ktor.foodies.server/
├── WebApp.kt                          # Entrypoint — calls module(), then app()
├── WebAppModule.kt                    # Thin orchestrator: calls cartModule(), menuModule(), securityModule()
├── Config.kt                          # App-level config (host, port, telemetry, redis, security, menu, basket)
├── home/
│   ├── HomeModule.kt                  # Wires home routes
│   └── HomeRoutes.kt                  # Home page route + HTML (moved from htmx/Home.kt)
├── cart/
│   ├── CartModule.kt                  # Wires CartService, returns CartModule data class
│   ├── CartRoutes.kt                  # HTTP routes + HTMX fragments (moved from htmx/cart/)
│   ├── CartService.kt                 # Interface (renamed from BasketService)
│   ├── HttpCartService.kt             # HTTP impl (renamed from HttpBasketService)
│   └── CartDomain.kt                  # Domain models + DTOs (moved from htmx/basket/Domain.kt)
├── menu/
│   ├── MenuModule.kt                  # Wires MenuService, returns MenuModule data class
│   ├── MenuRoutes.kt                  # HTTP routes + HTMX fragments (moved from htmx/menu/)
│   ├── MenuService.kt                 # Interface (unchanged)
│   └── HttpMenuService.kt             # HTTP impl (unchanged)
├── security/
│   ├── SecurityModule.kt              # Wires session storage, returns SecurityModule data class
│   ├── JWT.kt
│   ├── RedisSessionStorage.kt
│   ├── Security.kt
│   └── UserSessionScope.kt
└── shared/
    └── HtmxSyntax.kt                  # Shared HTMX/HTML helpers (moved from htmx/Syntax.kt)
```

---

## Changes Required

### 1. Create `cart/` feature package

| Action | Detail |
|--------|--------|
| Move `htmx/basket/Domain.kt` | → `cart/CartDomain.kt`, update package declaration |
| Move `htmx/basket/BasketService.kt` | → `cart/CartService.kt` + `cart/HttpCartService.kt`, rename interface to `CartService`, rename impl to `HttpCartService` |
| Move `htmx/cart/CartRoutes.kt` | → `cart/CartRoutes.kt`, update package and imports |
| Create `cart/CartModule.kt` | Extracts wiring of `HttpCartService` out of `WebAppModule` |

### 2. Create `menu/` feature package

| Action | Detail |
|--------|--------|
| Move `htmx/menu/MenuService.kt` | → `menu/MenuService.kt` + `menu/HttpMenuService.kt`, split interface from impl |
| Move `htmx/menu/MenuRoutes.kt` | → `menu/MenuRoutes.kt`, update package and imports |
| Create `menu/MenuModule.kt` | Extracts wiring of `HttpMenuService` out of `WebAppModule` |

### 3. Create `home/` feature package

| Action | Detail |
|--------|--------|
| Move `htmx/Home.kt` | → `home/HomeRoutes.kt`, update package |
| Create `home/HomeModule.kt` | Wires home routes |

### 4. Create `security/SecurityModule.kt`

Extract `RedisClient`, `RedisSessionStorage`, and `HealthCheckRegistry` wiring for Redis out of `WebAppModule` into `security/SecurityModule.kt`.

### 5. Move shared HTMX helpers

| Action | Detail |
|--------|--------|
| Move `htmx/Syntax.kt` | → `shared/HtmxSyntax.kt`, update package |

### 6. Refactor `WebAppModule.kt`

- `WebAppModule` data class holds `menuModule`, `cartModule`, `securityModule`, `homeModule`, `readinessCheck`.
- `module()` function becomes a thin orchestrator: calls `cartModule()`, `menuModule()`, `securityModule()`, `homeModule()`.
- HTTP client construction stays in `WebAppModule` as shared infrastructure and is passed into feature module functions.

### 7. Refactor `WebApp.kt`

- `app()` delegates route installation to each feature module's install function.
- No direct references to `htmx.*` packages remain.

### 8. Delete `htmx/` package

Remove the `htmx/` directory entirely once all files are moved.

### 9. Update tests

Mirror the new main source tree in the test source tree:
- Move `session/RedisSessionStorageSpec.kt` → `security/RedisSessionStorageSpec.kt`
- Move `session/UserSessionScopeSpec.kt` → `security/UserSessionScopeSpec.kt`
- Move `session/TestSyntax.kt` → `security/TestSyntax.kt`

---

## Non-Goals

- No changes to HTTP API contracts or HTMX behaviour.
- No changes to other microservice modules.
- No new features.

---

## Definition of Done

- [ ] No source file lives under `htmx/`.
- [ ] Each feature package (`cart/`, `menu/`, `home/`, `security/`) contains a `<Feature>Module.kt`.
- [ ] `WebAppModule.kt` contains no direct service/client construction — only calls to feature module functions.
- [ ] All existing tests pass (`./gradlew :webapp:jvmTest`).
- [ ] Package declarations and imports are consistent with the new structure.
