# Ktor Server setup

## Table of Contents

- [Main (App.kt)](#main-appkt)
- [Config](#config)
    - [File-based configuration](#file-based-configuration)
        - [Adding new configuration](#adding-new-configuration)
    - [Code-based configuration](#code-based-configuration)
- [Dependencies](#dependencies)
    - [Encapsulation](#encapsulation)
        - [Why manual dependency management?](#why-manual-dependency-management)
- [Testing](#testing)

This document explains how to set up a project with Ktor with best practices, including testing.
See [deployment](deployment.md) for details on building and deploying.

## Main (App.kt)

Our main function is located in [App.kt](../server/src/main/kotlin/io/ktor/foodies/server/App.kt), and starts an
`embeddedServer` using the `Netty` engine. Which for a modern service is the clear engine choice, unless you have a
very specific reason to use another engine or run on a different platform than JVM.

```kotlin
fun main() {
    embeddedServer(Netty, host = "0.0.0.0", port = 8080) {
        // Application.() -> Unit
    }.start(wait = true)
}
```

## Config

Loading the configuration is pretty straightforward. Ktor offers a way of easily loading `yaml`, and `hocon` files,
so while using other libraries is possible it is often not required. If you do not like splitting your configuration
from your code, you can also define it directly in code of course.

#### File-based configuration

In this project we are using `yaml`, so we defined [application.yaml](../server/src/main/resources/application.yaml),
and we can load it using `ApplicationConfig("application.yaml")`.

You can then load any key-value pair defined in your config using `config.property(keyName)`, and turn it into a
`@Seriazable` type using `getAs<Type>()`.

For example, in our case we want to load a `host` and `port` from our enviroment as `$HOST` and `$PORT`:

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8080"
```

```kotlin
@Serializable
data class Config(val host: String, val port: Int)

val config = ApplicationConfig("application.yaml").config("config").getAs<Config>()
```

##### Adding new configuration

Adding new configuration values requires the following steps:

1. Add a key to the config file either as root or under `config`. Defining them under `config` is convenient since you
   can load everything at once, and ensure encapsulation through types.
2. Optional: define a `@Seriazable` type for complex values.
3. Add the key to the `Config` class with the correct type.

Let’s add configuration for our database connection `jdbcUrl`, `username`, and `password`.

```yaml
config:
  host: "$HOST:0.0.0.0"
  port: "$PORT:8080"
  data_source:
    url: "$DB_URL:jdbc:postgresql://localhost:5432/foodies-database"
    username: "$DB_USERNAME:foodies_admin"
    password: "$DB_PASSWORD:foodies_password"
```

```kotlin
@Serializable
data class Config(
    val host: String,
    val port: Int,
    @SerialName("data_source") val dataSource: DataSource,
) {
    @Serializable
    data class DataSource(val url: String, val username: String, val password: String)
}

val config = ApplicationConfig("application.yaml").config("config").getAs<Config>()
```

#### Code-based configuration

If you want to avoid working with a file-based configuration, you can also easily define it directly in code:

```kotlin
@Serializable
data class Config(
    val host: String = System.getenv("HOST") ?: "0.0.0.0",
    val port: Int = System.getenv("PORT")?.toInt() ?: 8080,
)
```

## Dependencies

Now that we've loaded our configuration, we can finally load our dependencies and start doing some actual work!
We just added the `data_source` key to our `application.yaml` file, and we've loaded the `config` into memory.
One critical step is when we create our database connection using the configuration, we also need to make sure it closes
when the server is stopped. To ensure this, we use the `monitor.subscribe` handler. Note, we need to manually take care
of correctly ordering the finalizers. (might need an addition later).

```kotlin
fun Application.hikari(config: Config): HikariDataSource {
    val hikari = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.dataSource.url
        username = config.dataSource.username
        password = config.dataSource.password
    })
    monitor.subscribe(ApplicationStopped) { hikari.close() }
    return hikari
}
```

### Encapsulation

It's important to properly encapsulate and hide your dependencies. We'll define some simple types to encapsulate and
separate our concerns. I.e., routes should only wrap service calls and not have any knowledge of the database or other
external dependencies.

```kotlin
class Module(
    val dataSource: HikariDataSource,
    val menuService: MenuService,
    val userService: UserService
)

fun Application.routes(module: Module) = routing {
    userRoutes(module.userService)
    menuService(module.menuService)
}

fun Route.userRoutes(userService: UserService) {
    get("/users") { userService.getAll() }
}

fun Route.menuService(menuService: MenuService) {
    get("/foods") { menuService.getAll() }
}
```

To prevent the `Module` from growing infinitely, you should split in logical groups as fits your project.

```kotlin
class Module(
    val dataSource: HikariDataSource,
    val customers: Customers,
    val ordering: Ordering
) {
    class Customers(val userService: UserService, val loyaltyService: LoyaltyService)
    class Ordering(val menuService: MenuService, val orderService: OrderService)
}
```

#### Why manual dependency management?

I get this question all the time, and I think it's a good question to ask. Manually modeling and managing dependencies
forces me to think more deeply about how the application starts and how it's structured. It allows for more easily
adopting `Deferred` dependencies to parallelize the application initialization.

The biggest argument I hear against it is that it's too much work, or requires too much boilerplate but I've found this
not the case in practice. This project aims to show it again. (lets see how it goes).

## Testing

The app is clearly split between:

1. Loading configuration, and creating the `embeddedServer`
2. Loading the dependencies from the config into `Module`
3. Creating the `app` using the `Module`

For testing we use `testApplication` instead of `embeddedServer`, and load a test configuration with using
testcontainers.

This project uses (`TestBalloon`)[], but the same principles apply to Kotest or Kotlin Test.
See [TestModule.kt](../server/src/test/kotlin/io/ktor/foodies/server/TestModule.kt) to see how testcontainers are used
to set up a virtual enviroment and a test version of the `Module.`

This allows us to write test suites like this, where `testModule` is a `testFixture` shared for every test in the
`testSuite`.

```kotlin
val containerSpec by testSuite {
    val module = testModule()

    testApplication("app") {
        application { app(module()) }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

In this case we choose **not to** use singleton test containers, and prefer to create a new container for every `testSuite`.
Since the `testModule` contains all the dependencies, this means the `HikariDataSource` is also shared between tests.
If you **need to** use singleton containers make sure that it also only uses a single `HikariDataSource`,
since creating many (for every test) `HikariDataSource` against a single server can cause issues and flakyness. 
