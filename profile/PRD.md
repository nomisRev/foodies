# PRD: Refactor Profile Service to Feature-Packaged Structure

## Overview

Refactor the `profile` Ktor microservice to align with the established feature-packaged structure defined in the
project's package structure conventions. The current codebase uses a wrong root package, mixes domain model, table
definition, repository interface, and Exposed implementation in a single file, and places the event consumer in a
technical-layer package (`consumers/`) instead of a feature package.

## Goals

- Move all source files to the correct root package: `io.ktor.foodies.profile`
- Separate concerns into the canonical feature-packaged layout
- Mirror the refactored main source tree in the test source tree
- Keep all existing behaviour and tests green

## Current Structure

```
io.ktor.foodies.server/                         ← wrong root package
├── ProfileApp.kt
├── ProfileModule.kt
├── Config.kt
├── consumers/
│   └── UserEventConsumer.kt                    ← technical-layer package
└── profile/
    └── ProfileRepository.kt                    ← Profile domain model + ProfileTable + interface + ExposedImpl all in one file
```

Test tree mirrors the same wrong packages, with an extra stray `customers/` package.

## Target Structure

```
io.ktor.foodies.profile/
├── ProfileApp.kt
├── ProfileModule.kt                            ← thin orchestrator: calls userSyncModule()
├── Config.kt
├── Model.kt                                    ← Profile data class (shared domain model)
├── persistence/
│   ├── ProfileTable.kt                         ← Exposed table object
│   ├── ProfileRowMapping.kt                    ← ResultRow → Profile mapping
│   └── ProfileRepository.kt                   ← shared interface (findBySubject, insertOrIgnore, upsert, deleteBySubject) + ExposedProfileRepository
└── usersync/
    ├── UserSyncModule.kt                       ← wires ExposedProfileRepository + userSyncEventConsumer
    ├── UserSyncEventConsumer.kt                ← renamed from UserEventConsumer; depends on ProfileRepository
    └── UserSyncConfig.kt                       ← rabbit queue config extracted from root Config (if needed)
```

Test tree:

```
io.ktor.foodies.profile/
├── HealthCheckSpec.kt
├── TestSyntax.kt
├── persistence/
│   └── ProfileRepositorySpec.kt               ← moved from profile/ and customers/
└── usersync/
    └── UserSyncEventConsumerSpec.kt            ← renamed from NewUserConsumerSpec
```

## Detailed Tasks

### 1. Rename root package

- Change every `package io.ktor.foodies.server` declaration to `package io.ktor.foodies.profile`.
- Update all `import io.ktor.foodies.server.*` references accordingly.
- Update `build.gradle.kts` if it references the old package (e.g. `mainClass`).

### 2. Create `Model.kt`

- Extract the `Profile` data class from `profile/ProfileRepository.kt` into `Model.kt` at the service root package.

### 3. Create `persistence/` layer

- Create `persistence/ProfileTable.kt`: move `ProfileTable` Exposed object out of `ProfileRepository.kt`.
- Create `persistence/ProfileRowMapping.kt`: move the `ResultRow.toProfile()` (currently `toCustomer()`) extension
  function; rename it to `toProfile()`.
- Create `persistence/ProfileRepository.kt`:
  - Keep the `ProfileRepository` interface.
  - Keep `ExposedProfileRepository`; import `ProfileTable` from `persistence/`.
  - Remove the `Profile` data class (now in `Model.kt`).

### 4. Create `usersync/` feature package

- Create `usersync/UserSyncEventConsumer.kt`: move and rename `consumers/UserEventConsumer.kt`; update imports.
- Create `usersync/UserSyncModule.kt`:
  - Extract the RabbitMQ subscriber wiring and `userEventConsumer` call out of root `ProfileModule.kt`.
  - Wire `ExposedProfileRepository` and `userSyncEventConsumer` here.
  - Return a `UserSyncModule` data class (or equivalent) consumed by root `ProfileModule`.
- Delete `consumers/` package.

### 5. Update `ProfileModule.kt`

- Reduce to a thin orchestrator: call `userSyncModule(...)` and aggregate results.
- Remove direct wiring of repository and consumer; delegate to `UserSyncModule.kt`.

### 6. Refactor tests

- Rename/move `consumers/NewUserConsumerSpec.kt` → `usersync/UserSyncEventConsumerSpec.kt`; update package and imports.
- Rename/move `profile/ProfileRepositorySpec.kt` → `persistence/ProfileRepositorySpec.kt`; update package and imports.
- Delete stray `customers/CustomerRepositorySpec.kt` (duplicate/obsolete) after verifying coverage is retained in
  `persistence/ProfileRepositorySpec.kt`.
- Update `TestSyntax.kt` package declaration.
- Update `HealthCheckSpec.kt` package declaration.

## Acceptance Criteria

- All source files use `io.ktor.foodies.profile` as root package.
- No file mixes more than one concern (table definition, domain model, repository interface, Exposed implementation).
- `consumers/` technical-layer package no longer exists.
- Test packages mirror the main source tree exactly.
- `./gradlew :profile:jvmTest` passes with no failures.
