package io.ktor.foodies.order

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.foodies.order.client.HttpBasketClient
import io.ktor.foodies.order.events.OrderEventConsumer
import io.ktor.foodies.order.events.handlers.*
import io.ktor.foodies.order.repository.ExposedOrderRepository
import io.ktor.foodies.order.service.*
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.rabbitmq.RabbitConfig as ExtRabbitConfig
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.dataSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OrderModule(
    val httpClient: HttpClient,
    val orderService: DefaultOrderService,
    val eventConsumer: OrderEventConsumer,
    val eventPublisher: RabbitOrderEventPublisher,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config): OrderModule {
    val dataSource = dataSource(config.database)

    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
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
        httpClient.close()
    }

    val eventPublisher = RabbitOrderEventPublisher(
        rabbitChannel,
        config.rabbit.exchange,
        config.rabbit.routingKey,
        "order.cancelled",
        "order.status-changed",
        "order.stock-confirmed",
        "order.awaiting-validation",
        "order.stock-returned"
    )
    val orderRepository = ExposedOrderRepository(dataSource.database)
    val orderService = DefaultOrderService(orderRepository, basketClient, eventPublisher)
    val gracePeriodService = GracePeriodService(config.order, orderService, this)
    orderService.setGracePeriodService(gracePeriodService)

    val notificationService = LoggingNotificationService()

    val eventConsumer = OrderEventConsumer(
        rabbitChannel,
        config.rabbit.exchange,
        StockConfirmedEventHandler(orderService),
        StockRejectedEventHandler(orderService),
        PaymentSucceededEventHandler(orderService),
        PaymentFailedEventHandler(orderService),
        OrderStatusChangedEventHandler(orderRepository, notificationService),
        this
    )
    eventConsumer.start()

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
    }

    return OrderModule(
        httpClient = httpClient,
        orderService = orderService,
        eventConsumer = eventConsumer,
        eventPublisher = eventPublisher,
        readinessCheck = readinessCheck
    )
}
