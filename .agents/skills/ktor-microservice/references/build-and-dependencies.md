---
name: kotlin-ktor-gradle-conventions
description: Guidelines for the Gradle configuration in this repository
license: APACHE-2.0
metadata:
  author: nomisRev
  version: "1.0.0"
---

## Required conventions

- Use Gradle Kotlin DSL (`build.gradle.kts`).
- Use version catalogs only.
- Use `libs` from `gradle/libs.versions.toml`.
- Use `ktorLibs` from the Ktor version catalog configured in `settings.gradle.kts`.
- Never hardcode dependency versions or coordinates in module build files.

## Plugin patterns

- Service modules (`webapp`, `menu`, `profile`, `basket`, `order`, `payment`):
  - `id("foodies.kotlin-conventions")`
  - `id("foodies.ktor-service-conventions")`
- Domain/event modules (`events-*`):
  - `id("foodies.kotlin-domain-conventions")`

## Ktor service build file template

```kotlin
plugins {
    id("foodies.kotlin-conventions")
    id("foodies.ktor-service-conventions")
}

application { mainClass = "io.ktor.foodies.server.<service>AppKt" }

dependencies {
    implementation(project(":server-shared"))
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.config.yaml)
    implementation(libs.logback)

    testImplementation(project(":server-shared-test"))
}
```

## Module registration

- Add `include(":module")` in `settings.gradle.kts`.
- For nested modules, map `projectDir` explicitly (same pattern as `events-*`).

## Build and test commands

- Build one module: `./gradlew :<module>:build`
- Run module tests: `./gradlew :<module>:jvmTest`
- Run selected tests with TestBalloon-compatible selectors:
  - `./gradlew cleanJvmTest jvmTest --tests "com.example.TestSuite|inner suite|*" --no-build-cache`

## Local deployment tasks from convention plugin

`foodies.ktor-service-conventions` adds:

- `publishImageToLocalRegistry`
- `localRestartService` (`publishImageToLocalRegistry` & rollout restart for `deployment/<module>` in namespace `foodies`)
- `localReadinessCheck` (wait for deployment availability)

Service modules should also configure:

```kotlin
ktor {
    docker {
        localImageName = "foodies-<service>"
        imageTag = project.version.toString()
    }
}
```
