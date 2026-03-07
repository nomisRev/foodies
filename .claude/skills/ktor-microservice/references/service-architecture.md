# Service Architecture

Use the existing Foodies Ktor layout and manual dependency wiring.

## Module structure

Typical service package (`src/main/kotlin/io/ktor/foodies/<service>/`) contains a mix of these elements, depending on the module type:

- `<Service>App.kt`: entrypoint and Ktor plugin setup
- `Config.kt`: `@Serializable` config types loaded from `application.yaml`
- `<Service>Module.kt`: dependency graph assembly
- `Routes.kt`: HTTP route definitions
- `Service.kt`: business logic layer
- `Repository.kt`: data access layer (for services that own persistence)
- `<Dependency>Service.kt`: downstream service communication boundary
- `<vendor-or-capability>/`: named package for shared third-party wrappers (for example `featureflags/` for `LaunchDarkly`)
- `events/`: RabbitMQ publishers/consumers and handlers

Keep the boundary explicit:

```
Routes -> Service -> Repository -> Exposed
                  -> Client -> Ktor
                  -> Integration -> VendorSdk
```

## App bootstrap pattern

Use `ApplicationConfig("application.yaml").property("config").getAs<Config>()`, then start Netty.

```kotlin
fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val (_, openTelemetry) = monitoring(config.telemetry)
        app(module(config, openTelemetry))
    }.start(wait = true)
}
```

`app(module)` should install core plugins and register routes.

## Dependency wiring — root or two-level pattern

For services with feature packages, wiring can be split across two levels, or done directly in root module when that is clearer.

**Root `<Service>Module.kt`** — thin orchestrator. Creates shared infrastructure, then delegates to feature module functions:

```kotlin
fun Application.module(config: Config, telemetry: OpenTelemetry): OrderModule {
    val dataSource = dataSource(config.database, telemetry)
    Flyway.configure().dataSource(dataSource.hikari).load().migrate()

    val httpClient = buildHttpClient(telemetry)
    val rabbitChannel = buildRabbitChannel(config.rabbit)

    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        httpClient.close()
    }

    val publisher = Publisher(rabbitChannel, config.rabbit.exchange, Json)
    val subscriber = RabbitMQSubscriber(rabbitChannel, config.rabbit.exchange)

    val placement = placementModule(config, dataSource, publisher, httpClient)
    val tracking  = trackingModule(config, dataSource, publisher)
    val fulfillment = fulfillmentModule(config, dataSource, publisher, subscriber)
    val admin = adminModule(tracking, fulfillment)

    val readinessCheck = buildReadinessChecks(dataSource, rabbitChannel)
    return OrderModule(placement, tracking, fulfillment, admin, readinessCheck)
}
```

**Feature `<Feature>Module.kt`** — owns wiring for that vertical slice only:

```kotlin
// placement/PlacementModule.kt
fun placementModule(
    config: Config,
    dataSource: FoodiesDataSource,
    publisher: Publisher,
    httpClient: HttpClient,
): PlacementModule {
    val repo = ExposedPlacementRepository(dataSource.database)
    val basketClient = HttpBasketClient(httpClient, config.basket.baseUrl)
    val eventPublisher = RabbitPlacementEventPublisher(publisher)
    val service = DefaultPlacementService(repo, basketClient, eventPublisher, config.order)
    return PlacementModule(service)
}
```

Rules:
- Shared infrastructure (data source, rabbit channel, HTTP client) is created **once** in the root module and passed into feature module functions.
- Feature module functions create only their own repository/client/wrapper, service, publisher, and consumer instances.
- When a service needs capabilities from multiple repositories, inject those repositories separately (explicit wiring)
  instead of repository-interface inheritance.
- The root `<Service>Module` data class should expose only what `app(...)` needs (least powerful): either assembled
  feature modules or narrower dependencies such as services, consumers, and health checks.
- Route wiring in `<Service>App.kt` should depend on services, not repositories.
- Close shared resources (rabbit channel, HTTP client) in the root `ApplicationStopped` handler only.

Small modules can keep all wiring in a single root module when splitting into feature sub-modules does not add clarity.

## Configuration pattern

- Keep all runtime settings under `config` in `application.yaml`.
- Support environment overrides (`"$ENV:default"` style).
- Model nested settings as `@Serializable` nested data classes.
- Reuse shared config types where available:
  - `io.ktor.foodies.server.DataSource.Config`
  - `io.ktor.foodies.server.openid.Auth`

## Persistence and migrations

- Use Exposed for repositories.
- Run Flyway migrations during module initialization before serving traffic.
- Keep SQL schema evolution in module-local migration files.

## Messaging pattern

- Use exchange `foodies` (topic).
- Use typed event modules (`events-common`, `events-order`, etc.).
- Publish via `Publisher` wrappers from `rabbitmq-ext`.
- Consume with `RabbitMQSubscriber`.
- Keep handler logic in service/domain layer, not in route handlers.
