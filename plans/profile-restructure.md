# Plan: Profile Service Restructure

> Source PRD: profile/PRD.md

## Architectural decisions

- **Root package**: `io.ktor.foodies.profile` (renamed from `io.ktor.foodies.server`)
- **Schema**: `ProfileTable` stays unchanged; only moves to `persistence/ProfileTable.kt`
- **Key models**: `Profile` data class lives in root-level `Model.kt`; `ProfileRepository` interface + `ExposedProfileRepository` live in `persistence/ProfileRepository.kt`
- **Module boundaries**: `ProfileModule` is a thin orchestrator; all RabbitMQ/consumer wiring lives in `usersync/UserSyncModule`
- **Test tree**: mirrors main source tree exactly under `io.ktor.foodies.profile`

---

## Phase 1: Root package rename

**User stories**: Move all source files to the correct root package (`io.ktor.foodies.profile`)

### What to build

Change every `package io.ktor.foodies.server` declaration to `package io.ktor.foodies.profile` across all main and test source files. Update all cross-file imports that reference the old package. Update `build.gradle.kts` `mainClass` if it references the old package. No structural changes to files or directories yet — this is purely a package rename.

### Acceptance criteria

- [x] Every source file declares `package io.ktor.foodies.profile` (or a sub-package thereof)
- [x] No `import io.ktor.foodies.server` references remain anywhere (except for shared modules)
- [x] `build.gradle.kts` `mainClass` points to the new package (`io.ktor.foodies.profile.ProfileAppKt`)
- [x] `./gradlew :profile:jvmTest` passes with no failures

---

## Phase 2: Persistence layer extraction

**User stories**: Separate `ProfileTable`, row mapping, domain model, and repository into distinct files under `persistence/`

### What to build

Extract the `Profile` data class into a new root-level `Model.kt`. Split the single `ProfileRepository.kt` into three files under a new `persistence/` sub-package: `ProfileTable.kt` (Exposed table object), `ProfileRowMapping.kt` (the `ResultRow.toProfile()` extension, renamed from `toCustomer()`), and `ProfileRepository.kt` (the `ProfileRepository` interface + `ExposedProfileRepository` implementation importing from the new sibling files). The old `profile/ProfileRepository.kt` is deleted.

### Acceptance criteria

- [x] `Model.kt` exists at the service root package and contains only the `Profile` data class
- [x] `persistence/ProfileTable.kt` contains only the `ProfileTable` Exposed object
- [x] `persistence/ProfileRowMapping.kt` contains only the `ResultRow.toProfile()` extension function
- [x] `persistence/ProfileRepository.kt` contains the `ProfileRepository` interface and `ExposedProfileRepository`; no domain model or table definition
- [x] No file mixes more than one concern
- [x] `./gradlew :profile:jvmTest` passes with no failures

---

## Phase 3: UserSync feature package

**User stories**: Replace the technical-layer `consumers/` package with a feature-packaged `usersync/` package

### What to build

Create a `usersync/` sub-package containing three files. `UserSyncEventConsumer.kt` is the renamed and moved `consumers/UserEventConsumer.kt` with updated imports. `UserSyncModule.kt` extracts the RabbitMQ subscriber wiring and consumer call out of root `ProfileModule.kt`, wires `ExposedProfileRepository` and `UserSyncEventConsumer` internally, and exposes a result consumed by `ProfileModule`. `UserSyncConfig.kt` holds any RabbitMQ queue config extracted from root `Config.kt` if applicable. The old `consumers/` package is deleted. `ProfileModule.kt` is reduced to a thin orchestrator that delegates to `userSyncModule(...)`.

### Acceptance criteria

- [x] `usersync/UserSyncEventConsumer.kt` exists; `consumers/` package no longer exists
- [x] `usersync/UserSyncModule.kt` owns all RabbitMQ subscriber wiring
- [x] `ProfileModule.kt` contains no direct consumer or repository wiring; it delegates to `UserSyncModule`
- [x] `./gradlew :profile:jvmTest` passes with no failures

---

## Phase 4: Test tree cleanup

**User stories**: Mirror the refactored main source tree in the test source tree; remove stray/obsolete test files

### What to build

Move and rename test files to match the new package layout: `consumers/NewUserConsumerSpec.kt` → `usersync/UserSyncEventConsumerSpec.kt`, `profile/ProfileRepositorySpec.kt` → `persistence/ProfileRepositorySpec.kt`. Delete the stray `customers/CustomerRepositorySpec.kt` after confirming its coverage is retained in `persistence/ProfileRepositorySpec.kt`. Update package declarations and imports in `TestSyntax.kt` and `HealthCheckSpec.kt`.

### Acceptance criteria

- [x] Test package tree mirrors the main source tree exactly under `io.ktor.foodies.profile`
- [x] `usersync/UserSyncEventConsumerSpec.kt` exists; `consumers/NewUserConsumerSpec.kt` no longer exists
- [x] `persistence/ProfileRepositorySpec.kt` exists; `profile/ProfileRepositorySpec.kt` and `customers/CustomerRepositorySpec.kt` no longer exist
- [x] `TestSyntax.kt` and `HealthCheckSpec.kt` declare the correct root package
- [x] `./gradlew :profile:jvmTest` passes with no failures
