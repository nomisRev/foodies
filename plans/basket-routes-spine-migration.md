# Plan: basket/routes — Spine Contract Module and BasketClient Removal

Date: 2026-03-09

## Goal

Introduce a new `:basket-routes` Gradle module that lives at `basket/routes/` and contains:

- Spine endpoint definitions for all basket HTTP endpoints
- Shared transport DTOs (`CustomerBasket`, `BasketItem`, `AddItemRequest`, `UpdateItemQuantityRequest`)
- Typed failure declarations (`400`, `404` as `String`)

Once the module exists, replace the three handwritten `BasketClient` implementations in `:order` and `:webapp` with `spine.client.request(...)` calls, and replace the route handlers in `:basket` with Spine server routes.

## Module Layout

```
basket/
├── routes/                        ← new :basket-routes module
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/ktor/foodies/basket/routes/
│       ├── BasketRoutes.kt        ← Spine resource + endpoint definitions
│       └── BasketDomain.kt        ← shared transport DTOs
└── src/                           ← existing :basket service (unchanged package)
    └── main/kotlin/io/ktor/foodies/basket/
        ├── BasketApp.kt
        ├── BasketDomain.kt        ← remove after migration (types move to :basket-routes)
        ├── BasketRoutes.kt        ← replace with Spine route(endpoint) { ... }
        └── ...
```

`settings.gradle.kts` registration mirrors the `events-*` pattern:

```kotlin
include(":basket-routes")
project(":basket-routes").projectDir = file("basket/routes")
```

## Dependency Model

```
:basket-routes
  depends on: libs.spine.api
              libs.serialization.json

:basket (server)
  adds: project(":basket-routes")
        libs.spine.server

:order (client)
  adds: project(":basket-routes")
        libs.spine.client

:webapp (client)
  adds: project(":basket-routes")
        libs.spine.client
```

## Phase 0 — Compatibility Spike

**Objective:** verify Spine 0.10.0 (built with Ktor 3.3.3) is compatible with Foodies' Ktor 3.4.1.

### Steps

1. Add Spine version + library entries to `gradle/libs.versions.toml`:

   ```toml
   [versions]
   spine = "0.10.0"

   [libraries]
   spine-api          = { module = "dev.opensavvy.spine:api",          version.ref = "spine" }
   spine-server       = { module = "dev.opensavvy.spine:server",       version.ref = "spine" }
   spine-client       = { module = "dev.opensavvy.spine:client",       version.ref = "spine" }
   ```

2. Create `basket/routes/build.gradle.kts` with `foodies.kotlin-domain-conventions` + `libs.spine.api` + `libs.serialization.json`.

3. Register the module in `settings.gradle.kts`.

4. Run:

   ```
   ./gradlew :basket-routes:build
   ./gradlew :basket:build :order:build :webapp:build
   ```

5. If dependency conflicts arise (Ktor/Kotlin mismatch), investigate forced transitive resolution before proceeding.

**Gate:** all four modules compile and their existing tests pass before Phase 1 begins.

## Phase 1 — Create `:basket-routes` Contract Module

### 1.1 Define Spine resources and endpoints in `basket/routes/`

File: `basket/routes/src/main/kotlin/io/ktor/foodies/basket/routes/BasketRoutes.kt`

Define a Spine `RootResource` for `/basket` with the following endpoint declarations:

| Endpoint           | Method   | Path                      | In                         | Out             | Failures               |
|--------------------|----------|---------------------------|----------------------------|-----------------|------------------------|
| `Get`              | GET      | `/basket`                 | —                          | `CustomerBasket`| —                      |
| `Clear`            | DELETE   | `/basket`                 | —                          | Unit            | —                      |
| `AddItem`          | POST     | `/basket/items`           | `AddItemRequest`           | `CustomerBasket`| `400 String`, `404 String` |
| `UpdateItem`       | PUT      | `/basket/items/{itemId}`  | `UpdateItemQuantityRequest`| `CustomerBasket`| `400 String`, `404 String` |
| `RemoveItem`       | DELETE   | `/basket/items/{itemId}`  | —                          | `CustomerBasket`| `404 String`           |

Path parameter `{itemId}` is a `String` (Spine dynamic path parameters are always `String`).

### 1.2 Move transport DTOs to `:basket-routes`

File: `basket/routes/src/main/kotlin/io/ktor/foodies/basket/routes/BasketDomain.kt`

Move `CustomerBasket`, `BasketItem`, `AddItemRequest`, `UpdateItemQuantityRequest` from `basket/src/…/BasketDomain.kt` to this file. Keep them in the new `io.ktor.foodies.basket.routes` package.

**Note:** `ValidatedAddItem`, `ValidatedUpdateQuantity`, and the `validate()` extension functions stay in `:basket` because they are server-side validation concerns only.

### 1.3 Update `:basket` to depend on `:basket-routes`

In `basket/build.gradle.kts`:

```kotlin
implementation(project(":basket-routes"))
implementation(libs.spine.server)
```

Remove `libs.serialization.json` if it becomes transitive from `:basket-routes`.

Update `basket/src/…/BasketDomain.kt` to remove the moved DTOs and import from `:basket-routes`. Remove the file when it only contains `ValidatedAddItem`, `ValidatedUpdateQuantity`, and the `validate()` functions (rename or keep as-is for server-only types).

## Phase 2 — Migrate `:basket` Server Routes

Replace `BasketRoutes.kt` with Spine server routes using `route(endpoint) { ... }`.

```kotlin
// basket/src/main/kotlin/io/ktor/foodies/basket/BasketRoutes.kt
fun Route.basketRoutes(basketService: BasketService) = secureUser {
    route(BasketApi.Get) {
        val buyerId = userPrincipal().userId
        respond(basketService.getBasket(buyerId))
    }

    route(BasketApi.Clear) {
        val buyerId = userPrincipal().userId
        basketService.clearBasket(buyerId)
        respond(Unit)           // 204 No Content
    }

    route(BasketApi.AddItem) {
        val buyerId = userPrincipal().userId
        val request = body()
        val validatedRequest = validate { request.validate() }
        val basket = basketService.addItem(buyerId, validatedRequest)
        if (basket == null) fail(BasketApi.AddItem, HttpStatusCode.NotFound, "Not found")
        else respond(basket)
    }

    route(BasketApi.UpdateItem) {
        val buyerId = userPrincipal().userId
        val itemId = idOf(BasketApi.UpdateItem)   // String
        val request = body()
        val validated = request.validate()
        val basket = basketService.updateItemQuantity(buyerId, itemId, validated)
        if (basket == null) fail(BasketApi.UpdateItem, HttpStatusCode.NotFound, "Not found")
        else respond(basket)
    }

    route(BasketApi.RemoveItem) {
        val buyerId = userPrincipal().userId
        val itemId = idOf(BasketApi.RemoveItem)   // String
        val basket = basketService.removeItem(buyerId, itemId)
        if (basket == null) fail(BasketApi.RemoveItem, HttpStatusCode.NotFound, "Not found")
        else respond(basket)
    }
}
```

**Behavior preservation checklist:**

- `GET /basket` → `200 CustomerBasket` (unchanged)
- `DELETE /basket` → `204 No Content` (unchanged)
- `POST /basket/items` → `200 CustomerBasket` | `400 String` | `404 String`
- `PUT /basket/items/{itemId}` → `200 CustomerBasket` | `400 String` | `404 String`
- `DELETE /basket/items/{itemId}` → `200 CustomerBasket` | `404 String`
- Auth: `secureUser` wrapper unchanged — all routes still require authenticated user
- Telemetry/middleware: no change (Ktor pipeline plugins apply independently of Spine)

## Phase 3 — Migrate `:order` BasketClient

### 3.1 Add dependency

In `order/build.gradle.kts`:

```kotlin
implementation(project(":basket-routes"))
implementation(libs.spine.client)
```

### 3.2 Rewrite `HttpBasketClient`

Replace `order/src/main/kotlin/io/ktor/foodies/order/placement/BasketClient.kt`:

```kotlin
interface BasketClient {
    suspend fun getBasket(buyerId: String, token: String): CustomerBasket?
}

class SpineBasketClient(private val httpClient: HttpClient) : BasketClient {
    override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? =
        httpClient.request(BasketApi.Get)
            .handle(/* no declared failures for Get */)
            .bodyOrNull()
}
```

**Key:** `BasketApi.Get` has no declared failures, so a 404 that was previously caught becomes a thrown exception (none expected in current flow — the existing code returned `null` only on 404 which never occurs for `GET /basket`). Use `bodyOrNull()` if a 404 is still a possible infra response.

### 3.3 Remove duplicate local DTOs

Delete `BasketItem` and `CustomerBasket` from `BasketClient.kt` (they are now imported from `:basket-routes`). Update `PlacementService` and `PlacementModule` imports accordingly.

## Phase 4 — Migrate `:webapp` BasketService

### 4.1 Add dependency

In `webapp/build.gradle.kts`:

```kotlin
implementation(project(":basket-routes"))
implementation(libs.spine.client)
```

### 4.2 Rewrite `HttpBasketService`

Replace `webapp/src/main/kotlin/io/ktor/foodies/webapp/basket/HttpBasketService.kt` with Spine client calls:

```kotlin
class SpineBasketService(private val httpClient: HttpClient) : BasketService {

    override suspend fun getBasket(): CustomerBasket =
        httpClient.request(BasketApi.Get).bodyOrThrow()

    override suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket =
        httpClient.request(BasketApi.AddItem, AddItemRequest(menuItemId, quantity))
            .handle { on(HttpStatusCode.BadRequest) { error(it) } }
            .bodyOrThrow()

    override suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket =
        httpClient.request(BasketApi.UpdateItem(itemId), UpdateItemQuantityRequest(quantity))
            .handle { on(HttpStatusCode.BadRequest) { error(it) } }
            .bodyOrThrow()

    override suspend fun removeItem(itemId: String): CustomerBasket =
        httpClient.request(BasketApi.RemoveItem(itemId))
            .handle { on(HttpStatusCode.NotFound) { error(it) } }
            .bodyOrThrow()

    override suspend fun clearBasket() {
        httpClient.request(BasketApi.Clear).bodyOrThrow()
    }
}
```

### 4.3 Remove duplicate DTOs from `:webapp`

Delete `CustomerBasket`, `BasketItem`, `AddItemRequest`, `UpdateQuantityRequest` from `webapp/src/…/basket/BasketDomain.kt`. Import from `:basket-routes` instead. Update `BasketRoutes.kt` (webapp) imports.

**Note:** `UpdateQuantityRequest` in webapp is named differently from `UpdateItemQuantityRequest` in `:basket`. After migration both use `UpdateItemQuantityRequest` from `:basket-routes`.

## Acceptance Criteria

- [ ] `:basket-routes` module compiles with `./gradlew :basket-routes:build`
- [ ] All existing tests pass: `./gradlew :basket:test :order:test :webapp:test`
- [ ] No handwritten URL strings remain in `HttpBasketClient` (`:order`) or `HttpBasketService` (`:webapp`)
- [ ] `CustomerBasket`, `BasketItem`, `AddItemRequest`, `UpdateItemQuantityRequest` exist only in `:basket-routes`
- [ ] `order/placement/BasketClient.kt` and `webapp/basket/BasketDomain.kt` no longer define their own basket DTOs
- [ ] Declared failures (`400`, `404`) are handled via Spine typed mechanisms, not `catch (ClientRequestException)`

## File Change Summary

| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add `spine` version, `spine-api`, `spine-server`, `spine-client` entries |
| `settings.gradle.kts` | Add `include(":basket-routes")` + `projectDir` mapping |
| `basket/routes/build.gradle.kts` | Create new file |
| `basket/routes/src/…/BasketRoutes.kt` | Create — Spine resource + endpoint definitions |
| `basket/routes/src/…/BasketDomain.kt` | Create — shared transport DTOs |
| `basket/build.gradle.kts` | Add `:basket-routes` + `spine-server` dependencies |
| `basket/src/…/BasketDomain.kt` | Remove moved DTOs; keep `Validated*` types + `validate()` fns |
| `basket/src/…/BasketRoutes.kt` | Replace with Spine `route(endpoint) { ... }` |
| `order/build.gradle.kts` | Add `:basket-routes` + `spine-client` dependencies |
| `order/src/…/placement/BasketClient.kt` | Rewrite to `SpineBasketClient`; remove duplicate DTOs |
| `webapp/build.gradle.kts` | Add `:basket-routes` + `spine-client` dependencies |
| `webapp/src/…/basket/HttpBasketService.kt` | Rewrite to `SpineBasketService` |
| `webapp/src/…/basket/BasketDomain.kt` | Remove duplicate DTOs; import from `:basket-routes` |

## Risks and Notes

- **Spine `idOf()` returns `String`**: `itemId` is already a `String` in the basket domain, so no conversion is needed here. This is simpler than the menu case where IDs are `Long`.
- **`UpdateQuantityRequest` naming divergence**: webapp uses `UpdateQuantityRequest`, basket uses `UpdateItemQuantityRequest`. The canonical name after migration is `UpdateItemQuantityRequest` from `:basket-routes`. Webapp usages must be updated.
- **`token` parameter in `BasketClient`**: `HttpBasketClient` in `:order` accepts a `token` parameter but never uses it (auth propagation is handled by the shared `AuthContextPlugin` on `HttpClient`). The `SpineBasketClient` preserves this signature to avoid changing `PlacementService`'s call sites, but the parameter remains unused.
- **Phase 0 gate**: do not start Phase 2–4 until the compatibility spike confirms Spine 0.10.0 + Ktor 3.4.1 compile together without dependency resolution errors.
