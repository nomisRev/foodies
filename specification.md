# Spine Adoption Specification for Shared HTTP Contracts

Date: 2026-03-08

## Objective

Adopt Spine to define shared HTTP contracts once and use them on both server and client sides, so we stop duplicating:

- route definitions,
- client URL/method logic,
- transport DTOs and status handling.

Primary target: remove handwritten inter-service clients and duplicated DTOs for `menu` and `basket` flows used across `menu`, `basket`, `order`, and `webapp`, while adopting typed endpoint failures as first-class contracts.

## Current Problem in This Repository

The same HTTP APIs are currently represented multiple times:

- Handwritten clients
  - `basket/src/main/kotlin/io/ktor/foodies/basket/BasketMenuClient.kt`
  - `order/src/main/kotlin/io/ktor/foodies/order/placement/BasketClient.kt`
  - `webapp/src/main/kotlin/io/ktor/foodies/webapp/menu/HttpMenuService.kt`
  - `webapp/src/main/kotlin/io/ktor/foodies/webapp/basket/HttpBasketService.kt`
- Server routes are declared separately in producers
  - `menu/src/main/kotlin/io/ktor/foodies/menu/catalog/CatalogRoutes.kt`
  - `menu/src/main/kotlin/io/ktor/foodies/menu/admin/AdminRoutes.kt`
  - `basket/src/main/kotlin/io/ktor/foodies/basket/BasketRoutes.kt`
- Transport models are duplicated across modules
  - `MenuItem`/`MenuItemResponse`
  - `CustomerBasket`/`BasketItem`
  - add/update basket request DTOs

This creates drift risk and forces repeated error mapping (`404 -> null`, validation handling, path/query parsing).

## Spine Investigation Summary

### What Spine gives us

From docs and API reference, Spine provides:

- Shared endpoint schema in Kotlin code (`RootResource`, `StaticResource`, `DynamicResource`, endpoint builders).
- Typed endpoint metadata: HTTP method, request body, response body, query parameters, failures.
- Server integration via `route(endpoint) { ... }`, typed `body`, `parameters`, `respond(...)`, `fail(...)`.
- Client integration via `client.request(...)` returning `SpineResponse<Out, FailureSpec>` with:
  - `bodyOrThrow()`
  - `bodyOrNull()`
  - `handle(...)` for typed failure branches.
- Optional Arrow integration:
  - server: `routeWithRaise(...)`
  - client: `.body()` with `Raise`-based typed errors.

### Confirmed limitation relevant for Foodies

- Dynamic path parameters are `String` in Spine (`idOf(...)` returns `String`).
  - Impact: our `Long` path IDs (e.g., `/menu/{id}`) need explicit conversion/parsing.

### Version and compatibility facts (verified)

- Latest release: `0.10.0` (`maven-metadata.xml` last updated `20260304160120`).
- Spine 0.10.0 artifacts are built with:
  - Kotlin stdlib `2.2.21`
  - Ktor `3.3.3`
  - Coroutines `1.10.2`
  - Arrow `2.2.0` in `client-arrow`.
- Foodies currently uses:
  - Kotlin `2.3.10`
  - Ktor `3.4.1` (version catalog import in `settings.gradle.kts`).

Conclusion: compatibility is plausible but must be validated with a compile/test spike before broad rollout.

## Proposed Target Architecture

### New module

Create a new shared contract module:

- Module name: `:http-contracts` (recommended)
- Purpose: only transport contracts for HTTP APIs
  - Spine resources/endpoints
  - transport DTOs
  - query parameter classes
  - failure payload types (when needed)
- No domain service logic or persistence types.

### Dependency model

- `:http-contracts`
  - depends on `libs.spine.api`
  - depends on serialization libs used by DTOs
- Producer services (servers)
  - `:menu`, `:basket` depend on `:http-contracts` + `libs.spine.server`
- Consumer services (clients)
  - `:webapp`, `:basket`, `:order` depend on `:http-contracts` + `libs.spine.client`
- Optional later
  - `libs.spine.server.arrow`, `libs.spine.client.arrow`

### Version catalog additions

Add to `gradle/libs.versions.toml`:

```toml
[versions]
spine = "0.10.0"

[libraries]
spine-api = { module = "dev.opensavvy.spine:api", version.ref = "spine" }
spine-server = { module = "dev.opensavvy.spine:server", version.ref = "spine" }
spine-client = { module = "dev.opensavvy.spine:client", version.ref = "spine" }
spine-server-arrow = { module = "dev.opensavvy.spine:server-arrow", version.ref = "spine" }
spine-client-arrow = { module = "dev.opensavvy.spine:client-arrow", version.ref = "spine" }
```

### Contract Scope (Phase 1/2)

Start with endpoints that currently drive duplicated clients:

1. Menu catalog contract
- `GET /menu` with `offset`, `limit`
- `GET /menu/{id}`

2. Basket contract
- `GET /basket`
- `DELETE /basket`
- `POST /basket/items`
- `PUT /basket/items/{itemId}`
- `DELETE /basket/items/{itemId}`

Keep order tracking/admin out of first rollout.

### Typed Error Adoption (Required)

Typed errors are part of the baseline migration, not a follow-up.

Failure contract strategy:

- Use Spine `.failure<T>(HttpStatusCode.X)` for known business/validation failures.
- Keep auth failures (`401`/`403` from auth pipeline) as infra-level failures unless route logic itself returns them.
- For now, preserve existing response-body compatibility where tests rely on plain text by using `String` as failure payload where appropriate.

Initial failure mapping for migrated endpoints:

1. Menu catalog/admin
- `GET /menu`: `400` (invalid query parsing) as `String`
- `GET /menu/{id}`: `400` as `String`, `404` as `String`
- `POST /menu`: `400` as `String`
- `PUT /menu/{id}`: `400` as `String`, `404` as `String`
- `DELETE /menu/{id}`: `400` as `String`, `404` as `String`

2. Basket
- `POST /basket/items`: `400` as `String`, `404` as `String`
- `PUT /basket/items/{itemId}`: `400` as `String`, `404` as `String`
- `DELETE /basket/items/{itemId}`: `404` as `String`
- `GET /basket`, `DELETE /basket`: no business failure payloads declared

Client handling strategy:

- Replace `try/catch ClientRequestException` branches with `SpineResponse.handle(...)` for declared failures.
- Use `bodyOrThrow()` only where non-success is truly exceptional.
- Use explicit typed handling for known branches (especially existing `404 -> null` cases).

## Migration Strategy

### Phase 0: Compatibility spike

- Add catalog entries + dependency wiring only.
- Compile and run:
  - `./gradlew :http-contracts:build`
  - `./gradlew :menu:test :basket:test :order:test :webapp:test`
- If dependency conflict appears (Ktor/Kotlin alignment), decide:
  - wait for newer Spine release,
  - or pin/force compatible Ktor transitives after validation.

### Phase 1: Menu contract rollout

- Move menu transport DTOs and endpoint definitions to `:http-contracts`.
- Refactor `menu` routes to `route(MenuApi...)`.
- Replace consumers:
  - `basket` menu client uses `client.request(...)`.
  - `webapp` menu service uses `client.request(...)`.
- Remove duplicate menu transport DTOs from consumer modules.

### Phase 2: Basket contract rollout

- Move basket transport DTOs/requests + endpoint definitions to `:http-contracts`.
- Refactor `basket` routes to Spine server routes.
- Replace consumers:
  - `order` placement basket client
  - `webapp` basket service
- Remove duplicate basket DTOs in `order` and `webapp`.

### Phase 3: Hardening and optional Arrow

- Keep typed failures in place and verify all migrated clients use typed handling paths.
- Optionally introduce Arrow modules (`server-arrow`, `client-arrow`) if we want `Raise`-based flows after baseline adoption.

## Behavior and Semantics Requirements

- Keep current HTTP statuses during migration:
  - Menu: `200`, `201`, `204`, `400`, `404`
  - Basket: `200`, `204`, `400`, `401`, `404`
- Keep failure body compatibility for existing tests/consumers during initial migration:
  - `400`/`404` bodies remain plain text where currently expected.
- Preserve existing auth propagation:
  - continue using current shared `AuthContextPlugin` on `HttpClient`.
- Preserve telemetry:
  - continue using existing Ktor client telemetry plugin setup.
- Preserve path/query behavior:
  - explicitly parse dynamic `String` IDs to `Long` where needed.
  - return `400` for invalid numeric IDs to match existing behavior.

## Expected Benefits

- Single source of truth for endpoint path/method/body/parameters.
- Remove hardcoded URL strings from clients.
- Eliminate duplicated transport DTOs across modules.
- Safer client/server evolution with compile-time contract coupling.
- Cleaner failure handling path without control-flow exceptions.

## Risks and Mitigations

- Ktor/Kotlin version mismatch risk.
  - Mitigation: Phase 0 compile/test spike before broader migration.
- Dynamic path parameters are stringly typed.
  - Mitigation: shared parsing helpers and explicit invalid-ID failures.
- First migration complexity.
  - Mitigation: roll out in two slices (`menu` then `basket`), keep behavior parity tests.

## Acceptance Criteria

- `menu` and `basket` client/server contracts are declared once in `:http-contracts`.
- Manual inter-service URL construction is removed for migrated endpoints.
- Duplicated transport DTOs for migrated APIs are removed from consumer modules.
- Migrated clients handle declared failures via Spine typed mechanisms (`handle(...)` and explicit branches), not generic exception-based control flow.
- Existing module tests for `menu`, `basket`, `order`, `webapp` pass.

## Open Questions Requiring Your Decision

1. Should we include admin/order endpoints in the first Spine rollout, or keep initial scope to `menu` + `basket` only?
2. For typed failure payloads, do you want to keep plain-text compatibility initially (`String`) or move directly to structured JSON failure DTOs?
3. Is `:http-contracts` an acceptable module name, or do you prefer per-domain modules like `:http-contracts-menu` and `:http-contracts-basket` from day one?
4. If Spine 0.10.0 shows any incompatibility with Ktor `3.4.1`, do you prefer waiting for Spine update or applying a local compatibility strategy?

## Sources

- Spine setup and concepts:
  - https://spine.opensavvy.dev/setup.html
  - https://spine.opensavvy.dev/resources.html
  - https://spine.opensavvy.dev/endpoints.html
  - https://spine.opensavvy.dev/parameters.html
  - https://spine.opensavvy.dev/failures.html
  - https://spine.opensavvy.dev/failures-arrow.html
  - https://spine.opensavvy.dev/comparison-ktor-resources.html
- API docs:
  - https://spine.opensavvy.dev/api-docs/
  - https://spine.opensavvy.dev/api-docs/client/opensavvy.spine.client/request.html
  - https://spine.opensavvy.dev/api-docs/server/opensavvy.spine.server/route.html
  - https://spine.opensavvy.dev/api-docs/server/opensavvy.spine.server/respond.html
  - https://spine.opensavvy.dev/api-docs/server/opensavvy.spine.server/fail.html
  - https://spine.opensavvy.dev/api-docs/server/opensavvy.spine.server/-typed-response-scope/id-of.html
  - https://spine.opensavvy.dev/api-docs/server-arrow/opensavvy.spine.server.arrow/route-with-raise.html
  - https://spine.opensavvy.dev/api-docs/client-arrow/opensavvy.spine.client.arrow/body.html
- Release/news:
  - https://spine.opensavvy.dev/news/
  - https://spine.opensavvy.dev/news/2026/03/04/v0.10.0.html
  - https://spine.opensavvy.dev/news/2026/02/18/v0.9.2.html
  - https://spine.opensavvy.dev/news/2026/02/09/v0.9.1.html
  - https://spine.opensavvy.dev/news/2025/12/17/v0.9.0.html
- Maven metadata and POMs:
  - https://repo1.maven.org/maven2/dev/opensavvy/spine/api/maven-metadata.xml
  - https://repo1.maven.org/maven2/dev/opensavvy/spine/api/0.10.0/api-0.10.0.pom
  - https://repo1.maven.org/maven2/dev/opensavvy/spine/server/0.10.0/server-0.10.0.pom
  - https://repo1.maven.org/maven2/dev/opensavvy/spine/client/0.10.0/client-0.10.0.pom
  - https://repo1.maven.org/maven2/dev/opensavvy/spine/client-arrow/0.10.0/client-arrow-0.10.0.pom
