# Plan: Menu Service — Feature-Based Package Restructure

> Source PRD: menu/PRD.md

## Architectural decisions

- **Routes**: Unchanged — `GET /menu`, `GET /menu/{id}`, `POST /menu`, `PUT /menu/{id}`, `DELETE /menu/{id}`
- **Schema**: Unchanged — single `menu_items` table; no migrations added or modified
- **Key models**: `MenuItem` (shared root model in `Model.kt`); `MenuItemResponse` + `toResponse()` co-located; request/validation models scoped to `admin/`
- **Package root**: `io.ktor.foodies.menu`; feature sub-packages: `persistence/`, `catalog/`, `admin/`, `stock/`
- **Wiring**: `MenuModule.kt` becomes a thin orchestrator delegating to `catalogModule()`, `adminModule()`, `stockModule()`
- **No behaviour change**: HTTP contracts, event schemas, and database migrations are out of scope

---

## Phase 1: Persistence layer extraction

**User stories**: Extract the Exposed table definition, row mapping, and shared repository operations into a dedicated `persistence/` package so all feature packages can depend on a stable, shared data layer.

### What to build

Move the `MenuItems` Exposed table object, the `ResultRow → MenuItem` mapping, and the shared repository interface (`findById`, `list`, `update`) plus its Exposed implementation into `persistence/`. No feature logic changes — this is purely a structural move. The rest of the codebase continues to compile and all existing tests pass.

### Acceptance criteria

- [x] `persistence/MenuItemsTable.kt` contains the Exposed table object
- [x] `persistence/MenuItemRowMapping.kt` contains the `ResultRow → MenuItem` mapping
- [x] `persistence/MenuRepository.kt` contains the shared interface (`findById`, `list`, `update`) and `ExposedMenuRepository` implementation
- [x] No other package references the old flat `Repository.kt` for table/mapping concerns
- [x] `./gradlew :menu:jvmTest` passes with no failures

---

## Phase 2: Catalog feature

**User stories**: Public read access to menu items — browse the full menu and retrieve a single item by ID.

### What to build

Extract `list()` and `get()` into a self-contained `catalog/` package. `CatalogRoutes` handles `GET /menu` and `GET /menu/{id}`. `CatalogService` owns the read logic. `CatalogModule` wires the service with the shared `persistence/` repository and registers the routes. Tests for catalog read paths move to `catalog/`.

### Acceptance criteria

- [x] `catalog/CatalogRoutes.kt` handles `GET /menu` and `GET /menu/{id}`
- [x] `catalog/CatalogService.kt` exposes `list()` and `get()` interface + implementation
- [x] `catalog/CatalogModule.kt` wires service and routes
- [x] `catalog/CatalogContractSpec.kt` covers read-path contract tests
- [x] `catalog/CatalogServiceSpec.kt` covers service unit behaviour
- [x] `./gradlew :menu:jvmTest` passes with no failures

---

## Phase 3: Admin feature

**User stories**: CRUD management of menu items — create, update, and delete items via authenticated admin routes.

### What to build

Extract `create()`, `update()`, and `delete()` into `admin/`. `AdminRequests.kt` holds `CreateMenuItemRequest`, `UpdateMenuItemRequest`, `CreateMenuItem`, `UpdateMenuItem`, and their `validate()` extensions (moved from `Domain.kt`). `AdminRepository` interface adds `create()` and `delete()`; `ExposedAdminRepository` implements it, delegating `findById`/`update` to the shared `ExposedMenuRepository`. `AdminModule` wires everything and registers `AdminRoutes` (`POST /menu`, `PUT /menu/{id}`, `DELETE /menu/{id}`). Tests for admin paths move to `admin/`.

### Acceptance criteria

- [x] `admin/AdminRequests.kt` contains all request models and `validate()` extensions
- [x] `admin/AdminRepository.kt` interface and `admin/ExposedAdminRepository.kt` implementation exist
- [x] `admin/AdminService.kt` exposes `create()`, `update()`, `delete()` interface + implementation
- [x] `admin/AdminRoutes.kt` handles `POST /menu`, `PUT /menu/{id}`, `DELETE /menu/{id}`
- [x] `admin/AdminModule.kt` wires service, repository, and routes
- [x] `admin/AdminContractSpec.kt`, `admin/AdminServiceSpec.kt`, `admin/AdminValidationSpec.kt` cover admin paths
- [x] `./gradlew :menu:jvmTest` passes with no failures

---

## Phase 4: Stock feature

**User stories**: Stock reservation and return driven by order events — validate and reserve stock when an order is placed; return stock when an order is cancelled.

### What to build

Extract `validateAndReserveStock()` and `returnStock()` into `stock/`. `StockRepository` interface and `ExposedStockRepository` implementation handle the DB operations (delegating `findById` to the shared repository). `StockService` owns the business logic and `StockValidationResult` sealed type. `StockEventPublisher` (renamed from `RabbitMenuEventPublisher`) publishes `StockConfirmedEvent` and `StockRejectedEvent`. `StockEventConsumer` (moved from `MenuEventConsumer`) consumes `StockValidationRequested` and `OrderCancelled`. `StockModule` wires all of these. Tests for stock paths move to `stock/`.

### Acceptance criteria

- [ ] `stock/StockService.kt` contains `validateAndReserveStock()`, `returnStock()`, and `StockValidationResult`
- [ ] `stock/StockRepository.kt` interface and `stock/ExposedStockRepository.kt` implementation exist
- [ ] `stock/StockEventPublisher.kt` contains `RabbitStockEventPublisher` (renamed)
- [ ] `stock/StockEventConsumer.kt` consumes `StockValidationRequested` and `OrderCancelled`
- [ ] `stock/StockModule.kt` wires service, repository, publisher, and consumer
- [ ] `stock/StockEventPublisherSpec.kt` covers event publishing behaviour
- [ ] `./gradlew :menu:jvmTest` passes with no failures

---

## Phase 5: Cleanup & final wiring

**User stories**: Remove all old flat-package files, update `MenuModule.kt` to delegate to sub-modules, and confirm the full test suite is green.

### What to build

Delete the now-empty flat files: `Routes.kt`, `Service.kt`, `Repository.kt`, `Domain.kt`, `events/MenuEventPublisher.kt`, `events/MenuEventConsumer.kt`. Update `MenuModule.kt` to call `catalogModule()`, `adminModule()`, and `stockModule()`, aggregating their consumers and health checks. Confirm `persistence/MenuRepositorySpec.kt` is in place. Run the full test suite.

### Acceptance criteria

- [ ] All old flat files (`Routes.kt`, `Service.kt`, `Repository.kt`, `Domain.kt`, `events/`) are deleted
- [ ] `MenuModule.kt` delegates entirely to `catalogModule()`, `adminModule()`, `stockModule()`
- [ ] `persistence/MenuRepositorySpec.kt` exists and passes
- [ ] No file remains under `io.ktor.foodies.menu` root except `MenuApp.kt`, `MenuModule.kt`, `Config.kt`, `Model.kt`
- [ ] `./gradlew :menu:jvmTest` passes with no failures and no mocks introduced
