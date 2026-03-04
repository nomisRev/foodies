package io.ktor.foodies.order

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.foodies.order.admin.AdminModule
import io.ktor.foodies.order.admin.adminModule
import io.ktor.foodies.order.fulfillment.FulfillmentModule
import io.ktor.foodies.order.fulfillment.fulfillmentModule
import io.ktor.foodies.order.persistence.ExposedOrderRepository
import io.ktor.foodies.order.placement.PlacementModule
import io.ktor.foodies.order.placement.placementModule
import io.ktor.foodies.order.tracking.TrackingModule
import io.ktor.foodies.order.tracking.trackingModule
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitConnectionHealthCheck
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.auth.AuthContextPlugin
import io.ktor.foodies.server.dataSource
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class OrderModule(
    val placement: PlacementModule,
    val tracking: TrackingModule,
    val fulfillment: FulfillmentModule,
    val admin: AdminModule,
    val readinessCheck: HealthCheckRegistry,
)

fun Application.module(config: Config, telemetry: OpenTelemetry): OrderModule {
    val dataSource = dataSource(config.database, telemetry)

    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
        install(AuthContextPlugin)
        install(KtorClientTelemetry) { setOpenTelemetry(telemetry) }
    }

    val rabbitFactory = rabbitConnectionFactory(
        config.rabbit.host, config.rabbit.port, config.rabbit.username, config.rabbit.password
    )
    val rabbitConnection = rabbitFactory.newConnection()
    val rabbitChannel = rabbitConnection.createChannel()

    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        runCatching { rabbitConnection.close() }
        httpClient.close()
    }

    val publisher = Publisher(rabbitChannel, config.rabbit.exchange, Json)
    val subscriber = RabbitMQSubscriber(rabbitConnection, config.rabbit.exchange)

    val orderRepository = ExposedOrderRepository(dataSource.database)

    val placement = placementModule(config.order, config.basket.baseUrl, dataSource, publisher, httpClient)
    val tracking = trackingModule(dataSource, orderRepository, publisher)
    val fulfillment = fulfillmentModule(config.rabbit.exchange, orderRepository, publisher, subscriber)
    val admin = adminModule(tracking, fulfillment)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(rabbitConnection), Duration.ZERO, 5.seconds)
    }

    return OrderModule(
        placement = placement,
        tracking = tracking,
        fulfillment = fulfillment,
        admin = admin,
        readinessCheck = readinessCheck,
    )
}
