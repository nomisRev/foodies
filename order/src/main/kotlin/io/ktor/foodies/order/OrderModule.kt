package io.ktor.foodies.order

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.foodies.order.client.HttpBasketClient
import io.ktor.foodies.order.events.orderEventConsumers
import io.ktor.foodies.order.events.handlers.*
import io.ktor.foodies.order.repository.ExposedOrderRepository
import io.ktor.foodies.order.service.*
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitConnectionHealthCheck
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.openid.KeycloakServiceTokenClient
import io.ktor.foodies.server.openid.ServiceClientConfig
import io.ktor.foodies.server.dataSource
import io.ktor.foodies.server.openid.serviceAuth
import io.ktor.foodies.server.openid.withServiceAuth
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OrderModule(
    val httpClient: HttpClient,
    val basketHttpClient: HttpClient,
    val orderService: DefaultOrderService,
    val consumers: List<Flow<Unit>>,
    val eventPublisher: RabbitOrderEventPublisher,
    val readinessCheck: HealthCheckRegistry
)

suspend fun Application.module(config: Config, telemetry: OpenTelemetry): OrderModule {
    val dataSource = dataSource(config.database, telemetry)

    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()

    val httpClient = HttpClient(Apache5) {
        install(ClientContentNegotiation) { json() }
        install(KtorClientTelemetry) {
            setOpenTelemetry(telemetry)
        }
    }

    val serviceClientConfig = ServiceClientConfig(
        issuer = config.auth.issuer,
        clientId = config.serviceClient.clientId,
        clientSecret = config.serviceClient.clientSecret,
        defaultScopes = config.serviceClient.defaultScopes
    )

    val basketHttpClient = httpClient.withServiceAuth(serviceClientConfig)

    val basketClient = HttpBasketClient(basketHttpClient, config.basket.baseUrl)

    val rabbitFactory =
        rabbitConnectionFactory(config.rabbit.host, config.rabbit.port, config.rabbit.username, config.rabbit.password)
    val rabbitConnection = rabbitFactory.newConnection()
    val rabbitChannel = rabbitConnection.createChannel()

    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        runCatching { rabbitConnection.close() }
        httpClient.close()
    }

    val eventPublisher = RabbitOrderEventPublisher(
        Publisher(rabbitChannel, config.rabbit.exchange, Json)
    )
    val orderRepository = ExposedOrderRepository(dataSource.database)
    val orderService = DefaultOrderService(orderRepository, basketClient, eventPublisher)
    val gracePeriodService = GracePeriodService(config.order, orderService, this)
    orderService.setGracePeriodService(gracePeriodService)

    val notificationService = LoggingNotificationService()

    val subscriber = RabbitMQSubscriber(rabbitConnection, config.rabbit.exchange)
    val consumers = orderEventConsumers(
        subscriber,
        config.rabbit.exchange,
        StockConfirmedEventHandler(orderService),
        StockRejectedEventHandler(orderService),
        PaymentSucceededEventHandler(orderService),
        PaymentFailedEventHandler(orderService),
        OrderStatusChangedEventHandler(orderRepository, notificationService)
    )

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(rabbitConnection), Duration.ZERO, 5.seconds)
    }

    return OrderModule(
        httpClient = httpClient,
        basketHttpClient = basketHttpClient,
        orderService = orderService,
        consumers = consumers,
        eventPublisher = eventPublisher,
        readinessCheck = readinessCheck
    )
}
