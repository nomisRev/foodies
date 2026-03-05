# Plan: Basket File Rename

> Source PRD: IMPLEMENTATION.md

## Architectural decisions

- **Package**: `io.ktor.foodies.basket` — unchanged throughout; only physical file names change
- **Symbols**: No class, interface, or function renames; Kotlin resolves symbols by package, not file name
- **Test tree**: No test file renames required; test imports resolve via package

---

## Phase 1: Rename all six basket source files to feature-prefixed names

**User stories**: As a developer navigating the project, I want each basket source file to be self-describing so I can locate it without ambiguity across modules.

### What to build

Rename the six generic flat-structure files in `basket/src/main/kotlin/io/ktor/foodies/basket/` to carry the `Basket` prefix, matching the convention already established by `BasketModule.kt` and `BasketApp.kt`. The rename is purely at the file-system level; package declarations, class names, and all symbols remain identical. No test files require renaming because the test source tree already uses feature-prefixed names or references symbols by package.

| Current name    | New name              |
|-----------------|-----------------------|
| `Routes.kt`     | `BasketRoutes.kt`     |
| `Service.kt`    | `BasketService.kt`    |
| `Repository.kt` | `BasketRepository.kt` |
| `Domain.kt`     | `BasketDomain.kt`     |
| `MenuClient.kt` | `BasketMenuClient.kt` |
| `Config.kt`     | `BasketConfig.kt`     |

### Acceptance criteria

- [x] All six files in `basket/src/main/kotlin/io/ktor/foodies/basket/` carry the `Basket` prefix
- [x] No symbols are renamed; only file names change
- [x] `./gradlew :basket:build` exits with code 0
- [x] `./gradlew :basket:jvmTest` exits with code 0
