# Testing

Follow repository testing rules:

- Never use mocks. Use real dependencies via Testcontainers.
- Wire tests through the actual production `module(config, telemetry)` function.
- Use TestBalloon + shared fixtures from `server-shared-test`.

## Core tools

- Test framework: TestBalloon
- Ktor test runtime: `ktor-server-test-host`
- Shared fixtures: `server-shared-test`
- Containers: PostgreSQL, RabbitMQ (and Redis/Keycloak where needed)

## Test structure patterns

- Use `testSuite {}` for isolated suites.
- Use `ctxSuite(context = { ... }) { ... }` when multiple tests share context.
- Use `testFixture { ... }` for expensive setup reused in a suite.

## Preferred: test through the production module

**Always wire tests through the real `module(config, telemetry)` function.** This validates the actual dependency graph and catches wiring mistakes that in-memory fakes would hide. Use Testcontainers to satisfy infrastructure dependencies.

```kotlin
fun TestSuite.orderContext(): OrderContext {
    val postgres = postgresContainer()
    val rabbit = rabbitContainer()
    val module = testFixture {
        val config = testConfig(postgres(), rabbit())
        Application().module(config, OpenTelemetry.noop())
    }
    return OrderContext(postgres, rabbit, module)
}

val placementServiceSpec by testSuite {
    val ctx = orderContext()

    test("should create order") {
        val order = ctx.module().placementService.createOrder(...)
        assertEquals(OrderStatus.Submitted, order.status)
    }
}
```

`testConfig(...)` builds a `Config` pointing at container host/port values.

## Integration app tests

- Use `testApplication("name") { ... }` from `server-shared-test`.
- Wire the real module — do not substitute feature services.
- Use `jsonClient()` helper for JSON APIs.

```kotlin
testApplication("POST /orders returns 201") {
    application { app(module(testConfig(postgres(), rabbit()), OpenTelemetry.noop())) }
    val response = jsonClient().post("/orders") { ... }
    assertEquals(HttpStatusCode.Created, response.status)
}
```

## Database and migration tests

- Flyway migrations run inside `module(...)` — no separate migration step needed in tests.
- Keep tests isolated: truncate or insert fresh rows per test rather than sharing state.

## Event-driven tests

- Use real RabbitMQ containers for publisher/consumer flows.
- Verify eventual outcomes with `eventually { ... }` from `server-shared-test`.

## What NOT to do

- **Do not create `InMemoryRepository` or `InMemoryEventPublisher` fakes.** They duplicate production logic, diverge silently, and give false confidence. Use the real repository backed by a Testcontainers PostgreSQL instance.
- **Do not instantiate feature services directly in tests** with hand-rolled dependencies. Go through `module(...)` so the wiring is tested too.

## Commands

- Run module tests: `./gradlew :<module>:test`
- Run selected tests:
  - `./gradlew cleanTest test --tests "com.example.TestSuite|inner suite|*" --no-build-cache`
