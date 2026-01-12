package io.ktor.foodies.order

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.foodies.order.client.HttpBasketClient
import io.ktor.foodies.order.events.OrderEventConsumer
import io.ktor.foodies.order.events.handlers.*
import io.ktor.foodies.order.repository.ExposedIdempotencyRepository
import io.ktor.foodies.order.repository.ExposedOrderRepository
import io.ktor.foodies.order.service.*
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.rabbitmq.RabbitConfig as ExtRabbitConfig
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.ValidationException
import io.ktor.foodies.server.dataSource
import io.ktor.foodies.server.openid.discover
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.flywaydb.core.Flyway

fun main() {
    val config = ApplicationConfig("application.yaml").property("config").getAs<Config>()
    embeddedServer(Netty, host = config.host, port = config.port) {
        val dataSource = dataSource(config.database)
        migrate(dataSource)
        security(config)
        app(config, dataSource)
    }.start(wait = true)
}

fun Application.app(config: Config, dataSource: DataSource) {
    install(ContentNegotiation) { json() }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString("\n"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "Invalid request")
        }
    }

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
    }
    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }

    val basketClient = HttpBasketClient(httpClient, config.basket.baseUrl)

    val rabbitFactory = rabbitConnectionFactory(
        ExtRabbitConfig(
            config.rabbit.host,
            config.rabbit.port,
            config.rabbit.username,
            config.rabbit.password
        )
    )
    val rabbitConnection = rabbitFactory.newConnection()
    val rabbitChannel = rabbitConnection.createChannel()
    rabbitChannel.exchangeDeclare(config.rabbit.exchange, "topic", true)

    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        runCatching { rabbitConnection.close() }
    }

    val eventPublisher = RabbitOrderEventPublisher(
        rabbitChannel,
        config.rabbit.exchange,
        config.rabbit.routingKey,
        "order.cancelled",
        "order.status-changed",
        "order.awaiting-validation",
        "order.stock-returned"
    )
    val orderRepository = ExposedOrderRepository(dataSource.database)
    val idempotencyRepository = ExposedIdempotencyRepository(dataSource.database)
    val idempotencyService = IdempotencyService(idempotencyRepository)
    val orderService = DefaultOrderService(orderRepository, basketClient, eventPublisher, idempotencyService)
    val gracePeriodService = GracePeriodService(config.order, orderService, this)
    orderService.setGracePeriodService(gracePeriodService)

    val notificationService = LoggingNotificationService()

    OrderEventConsumer(
        rabbitChannel,
        config.rabbit.exchange,
        StockConfirmedEventHandler(orderService),
        StockRejectedEventHandler(orderService),
        PaymentSucceededEventHandler(orderService),
        PaymentFailedEventHandler(orderService),
        OrderStatusChangedEventHandler(orderRepository, notificationService),
        this
    ).start()

    routing {
        healthz(dataSource)
        orderRoutes(orderService)
        adminRoutes(orderService)
    }
}

fun Route.healthz(dataSource: DataSource) {
    get("/healthz") { call.respond(HttpStatusCode.OK) }
    get("/healthz/ready") {
        val dbHealthy = runCatching {
            dataSource.hikari.connection.use { it.isValid(5) }
        }.getOrDefault(false)

        val status = if (dbHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(status, mapOf("database" to if (dbHealthy) "UP" else "DOWN"))
    }
}

private fun migrate(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
}

private suspend fun Application.security(config: Config) {
    val openIdConfig = HttpClient(CIO).use { it.discover(config.auth.issuer) }
    install(Authentication) {
        jwt {
            verifier(openIdConfig.jwksProvider(), config.auth.issuer)
            validate { credential ->
                if (!credential.payload.audience.contains(config.auth.audience)) {
                    return@validate null
                }
                credential.payload
            }
        }
    }
}
