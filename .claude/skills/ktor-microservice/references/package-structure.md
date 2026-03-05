# Package Structure

Organize packages by **domain feature**, not by technical layer. Group the vertical slice (routes, service, repository,
domain model) for each feature together in one package.

## Guiding principles

- **Feature-first**: top-level packages inside a service represent domain features or sub-domains, not layers like
  `routes/`, `service/`, `repository/`.
- **Feature-prefixed file names**: use `CatalogRoutes.kt`, `CatalogService.kt` — not generic `Routes.kt`, `Service.kt`.
  Avoids ambiguous IDE tabs.
- **Split interfaces per feature**: when a service has multiple features, split the service interface so each feature
  owns its contract.
- **Split repositories per feature**: each feature defines its own repository interface **and its own Exposed repository
  implementation**. Do not create a single shared `ExposedXxxRepository` that implements multiple feature interfaces.
- **Shared repository for cross-cutting operations**: when multiple features need the same operations on a shared
  aggregate (e.g. `findById`, `update`), extract a shared repository interface and implementation into `persistence/`.
  Feature repositories extend the shared interface and delegate via Kotlin's `by` keyword.
  See [Shared repository pattern](#shared-repository-pattern).
- **Exposed table objects in `persistence/`**: Exposed `object` table definitions are shared infrastructure. They live
  in `persistence/<Aggregate>Tables.kt`. Feature repositories import from `persistence/` — they do not own or duplicate
  table definitions.
- **Per-feature event publishers**: each feature publishes only the events it owns.
- **Per-feature dependency wiring**: each feature assembles its own dependencies in a `<Feature>Module.kt` function
  inside the feature package. The root `<Service>Module.kt` calls each feature module function and returns the assembled
  `<Service>Module` data class.
- **Shared domain models at root**: domain models used across features live in `Model.kt` at the service root package.
- **Bootstrap at root**: `<Service>App.kt` and `<Service>Module.kt` stay at the service root. `<Service>Module.kt` is a
  thin orchestrator that calls feature module functions.
- **Config split**: app-level config (`host`, `port`, `telemetry`, `database`, `rabbitmq`) stays in root `Config.kt`.
  Feature-specific config lives in the feature package.
- **Tests mirror main**: test packages mirror the main source tree exactly.

## When to apply feature packages

Apply feature-based sub-packages when a service has **multiple distinct responsibilities** or sub-domains (typically 2+
independent route groups, separate event flows, or distinct business processes).

Keep a **flat structure** when a service has a single aggregate with one set of operations, or is too small to benefit
from sub-packages (< 5 source files).

## Feature-packaged service structure

```
io.ktor.foodies.<service>/
├── <Service>App.kt                        # Entrypoint — calls module(), then app()
├── <Service>Module.kt                     # Thin orchestrator: calls featureAModule(), featureBModule()
├── Config.kt                              # App-level config (host, port, telemetry, db, rabbitmq)
├── Model.kt                               # Domain models shared across features
├── persistence/
│   ├── <Aggregate>Tables.kt              # Exposed table objects (shared infrastructure)
│   └── <Aggregate>Repository.kt          # Shared repository interface + impl (findById, update)
├── <featureA>/
│   ├── <FeatureA>Module.kt               # Wires feature slice: repo, service, publisher, routes
│   ├── <FeatureA>Routes.kt               # HTTP endpoints for this feature
│   ├── <FeatureA>Service.kt              # Business logic
│   ├── <FeatureA>Repository.kt           # Interface: feature-specific data access
│   ├── Exposed<FeatureA>Repository.kt    # Exposed impl, imports from persistence/
│   ├── <FeatureA>EventPublisher.kt       # Publishes only this feature's events
│   └── <FeatureA>Requests.kt             # Request/response DTOs
├── <featureB>/
│   ├── <FeatureB>Module.kt
│   ├── <FeatureB>Routes.kt
│   ├── <FeatureB>Service.kt
│   ├── <FeatureB>Repository.kt           # Extends shared repository if it needs findById/update
│   ├── Exposed<FeatureB>Repository.kt    # Delegates shared ops via `by`, adds feature-specific queries
│   ├── <FeatureB>EventPublisher.kt
│   ├── <FeatureB>EventConsumer.kt        # Message consumer (if applicable)
│   └── handlers/                          # Event handlers (if many)
│       └── <EventName>Handler.kt
└── <admin>/                               # Cross-cutting feature that composes from other features
    ├── <Admin>Module.kt                   # Imports services from other features
    └── <Admin>Routes.kt
```

## Flat service structure

```
io.ktor.foodies.<service>/
├── <Service>App.kt
├── <Service>Module.kt
├── Config.kt
├── Domain.kt                              # Domain models + DTOs + validation
├── Routes.kt
├── Service.kt
├── Repository.kt
└── events/                                # (if applicable)
    └── <EventName>Handler.kt
```

### Decision heuristic: where does an operation belong?

| Question                                                | Answer                                                                             |
|---------------------------------------------------------|------------------------------------------------------------------------------------|
| Is the operation used by 2+ features?                   | Put the interface + impl in `persistence/`.                                        |
| Is the operation specific to one feature?               | Put it in that feature's repository interface and `Exposed<Feature>Repository`.    |
| Does a handler or consumer only need shared operations? | Depend on common interface (from `persistence/`), not on a feature repository.     |
| Does a table have a FK to another table?                | Both tables stay in `persistence/` — the FK creates a compile-time dependency.     |
| Is a table only referenced by one feature's repository? | It still lives in `persistence/` — table objects are always shared infrastructure. |
