# PRD: Menu Service — Feature-Based Package Restructure

## Problem

The `menu` service currently uses a flat package structure with generic file names (`Routes.kt`, `Service.kt`,
`Repository.kt`, `Domain.kt`). The service has grown to cover three distinct responsibilities:

- **Catalog**: public read access to menu items (`list`, `get`)
- **Admin**: CRUD management of menu items (`create`, `update`, `delete`)
- **Stock**: stock reservation and return driven by order events (`validateAndReserveStock`, `returnStock`)

A flat structure makes it hard to reason about ownership, extend individual features, and enforce boundaries between
concerns. The service qualifies for feature-based sub-packages per the established package structure conventions.

---

## Goal

Reorganise the `menu` module into domain feature packages without changing any observable behaviour. All existing HTTP
contracts, event flows, and test coverage must remain intact after the refactor.

---

## Current Structure

```
io.ktor.foodies.menu/
├── MenuApp.kt
├── MenuModule.kt
├── Config.kt
├── Domain.kt                  # MenuItem, MenuItemResponse, CreateMenuItemRequest, UpdateMenuItemRequest,
│                              # CreateMenuItem, UpdateMenuItem, validate(), toResponse()
├── Routes.kt                  # GET /menu, GET /menu/{id}, POST /menu, PUT /menu/{id}, DELETE /menu/{id}
├── Service.kt                 # MenuService interface + MenuServiceImpl (all operations)
├── Repository.kt              # MenuRepository interface + ExposedMenuRepository (all operations + table def)
└── events/
    ├── MenuEventPublisher.kt  # StockConfirmedEvent, StockRejectedEvent, RabbitMenuEventPublisher
    └── MenuEventConsumer.kt   # Consumes StockValidationRequested + OrderCancelled events
```

---

## Target Structure

```
io.ktor.foodies.menu/
├── MenuApp.kt                                      # Unchanged
├── MenuModule.kt                                   # Thin orchestrator: calls catalogModule(), adminModule(), stockModule()
├── Config.kt                                       # Unchanged
├── Model.kt                                        # MenuItem (shared domain model used across all features)
│
├── persistence/
│   ├── MenuItemsTable.kt                           # Exposed table object (moved out of Repository.kt)
│   ├── MenuItemRowMapping.kt                       # Row → MenuItem mapping (extracted from ExposedMenuRepository)
│   └── MenuRepository.kt                           # Shared interface (findById, update, list) + ExposedMenuRepository impl
│
├── catalog/
│   ├── CatalogModule.kt                            # Wires CatalogService + CatalogRoutes
│   ├── CatalogRoutes.kt                            # GET /menu, GET /menu/{id}
│   ├── CatalogService.kt                           # Interface + impl: list(), get()
│   └── CatalogRepository.kt                       # Extends shared MenuRepository; no extra queries needed
│
├── admin/
│   ├── AdminModule.kt                              # Wires AdminService + AdminRoutes
│   ├── AdminRoutes.kt                              # POST /menu, PUT /menu/{id}, DELETE /menu/{id}
│   ├── AdminService.kt                             # Interface + impl: create(), update(), delete()
│   ├── AdminRepository.kt                          # Interface: create(), delete()
│   ├── ExposedAdminRepository.kt                  # Exposed impl; delegates findById/update via `by` to shared repo
│   └── AdminRequests.kt                            # CreateMenuItemRequest, UpdateMenuItemRequest, CreateMenuItem,
│                                                   # UpdateMenuItem, validate() extensions
│
└── stock/
    ├── StockModule.kt                              # Wires StockService, StockEventPublisher, StockEventConsumer
    ├── StockService.kt                             # Interface + impl: validateAndReserveStock(), returnStock()
    ├── StockRepository.kt                          # Interface: validateAndReserveStock(), returnStock()
    ├── ExposedStockRepository.kt                  # Exposed impl; delegates findById via `by` to shared repo
    ├── StockEventPublisher.kt                      # StockConfirmedEvent, StockRejectedEvent, RabbitStockEventPublisher
    └── StockEventConsumer.kt                       # Consumes StockValidationRequested + OrderCancelled
```

---

## Detailed Migration Plan

### 1. Create `persistence/`

| Action | Detail |
|--------|--------|
| Create `MenuItemsTable.kt` | Move the Exposed `object MenuItems : Table(...)` out of `Repository.kt` |
| Create `MenuItemRowMapping.kt` | Extract the `ResultRow → MenuItem` mapping function |
| Create `MenuRepository.kt` | Shared interface with `findById`, `update`, `list`; `ExposedMenuRepository` implements it using `MenuItemsTable` and `MenuItemRowMapping` |

### 2. Create `catalog/`

| Action | Detail |
|--------|--------|
| Create `CatalogService.kt` | Extract `list()` and `get()` from `MenuService`/`MenuServiceImpl` |
| Create `CatalogRepository.kt` | Interface that extends the shared `MenuRepository`; no additional queries |
| Create `CatalogRoutes.kt` | Move `GET /menu` and `GET /menu/{id}` from `Routes.kt` |
| Create `CatalogModule.kt` | Wire `CatalogService` with the shared repository and register `CatalogRoutes` |

### 3. Create `admin/`

| Action | Detail |
|--------|--------|
| Create `AdminRequests.kt` | Move `CreateMenuItemRequest`, `UpdateMenuItemRequest`, `CreateMenuItem`, `UpdateMenuItem`, and their `validate()` extensions from `Domain.kt` |
| Create `AdminRepository.kt` | Interface with `create()` and `delete()` |
| Create `ExposedAdminRepository.kt` | Implements `AdminRepository`; delegates `findById`/`update` via `by ExposedMenuRepository` |
| Create `AdminService.kt` | Extract `create()`, `update()`, `delete()` from `MenuService`/`MenuServiceImpl` |
| Create `AdminRoutes.kt` | Move `POST /menu`, `PUT /menu/{id}`, `DELETE /menu/{id}` from `Routes.kt` |
| Create `AdminModule.kt` | Wire `AdminService`, `ExposedAdminRepository`, and register `AdminRoutes` |

### 4. Create `stock/`

| Action | Detail |
|--------|--------|
| Create `StockRepository.kt` | Interface with `validateAndReserveStock()` and `returnStock()` |
| Create `ExposedStockRepository.kt` | Implements `StockRepository`; delegates `findById` via `by ExposedMenuRepository` |
| Create `StockService.kt` | Extract `validateAndReserveStock()` and `returnStock()` from `MenuService`/`MenuServiceImpl`; `StockValidationResult` moves here |
| Create `StockEventPublisher.kt` | Rename/move `MenuEventPublisher` content; rename `RabbitMenuEventPublisher` → `RabbitStockEventPublisher` |
| Create `StockEventConsumer.kt` | Move `MenuEventConsumer` content |
| Create `StockModule.kt` | Wire `StockService`, `ExposedStockRepository`, `RabbitStockEventPublisher`, `StockEventConsumer` |

### 5. Update `Model.kt`

Move `MenuItem` and `MenuItemResponse` (and `toResponse()`) to `Model.kt` at the service root. These are used by all
three features.

### 6. Update `MenuModule.kt`

Replace the monolithic wiring with calls to `catalogModule()`, `adminModule()`, and `stockModule()`. The returned
`MenuModule` data class aggregates consumers and health checks from all three feature modules.

### 7. Delete old flat files

Remove `Routes.kt`, `Service.kt`, `Repository.kt`, `Domain.kt`, `events/MenuEventPublisher.kt`,
`events/MenuEventConsumer.kt` once all content has been migrated.

### 8. Update tests

Mirror the new package structure in `src/test/`:

| Old test file | New location |
|---------------|--------------|
| `MenuContractSpec.kt` | Split into `catalog/CatalogContractSpec.kt` and `admin/AdminContractSpec.kt` |
| `MenuServiceSpec.kt` | Split into `catalog/CatalogServiceSpec.kt`, `admin/AdminServiceSpec.kt`, `stock/StockServiceSpec.kt` |
| `MenuRepositorySpec.kt` | `persistence/MenuRepositorySpec.kt` |
| `MenuValidationSpec.kt` | `admin/AdminValidationSpec.kt` |
| `events/MenuEventPublisherSpec.kt` | `stock/StockEventPublisherSpec.kt` |
| `HealthCheckSpec.kt` | Unchanged |
| `TestSyntax.kt` | Unchanged |

---

## Out of Scope

- No changes to HTTP API contracts or event schemas.
- No changes to database migrations.
- No changes to `Config.kt` or `MenuApp.kt`.
- No new features or business logic changes.

---

## Definition of Done

- [ ] All source files live in their target feature package; no files remain in the old flat locations.
- [ ] `MenuModule.kt` delegates to `catalogModule()`, `adminModule()`, and `stockModule()`.
- [ ] `persistence/` contains the Exposed table object, row mapping, and shared repository.
- [ ] Each feature package contains its own `*Module.kt`, `*Routes.kt`, `*Service.kt`, `*Repository.kt`, and
      `Exposed*Repository.kt` (where applicable).
- [ ] Test packages mirror the main source tree.
- [ ] `./gradlew :menu:jvmTest` passes with no failures.
- [ ] No mocks introduced; all tests use real dependencies via TestContainers.
