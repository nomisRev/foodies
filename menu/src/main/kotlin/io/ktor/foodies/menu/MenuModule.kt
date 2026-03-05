package io.ktor.foodies.menu

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.menu.admin.AdminRepository
import io.ktor.foodies.menu.admin.ExposedAdminRepository
import io.ktor.foodies.menu.persistence.ExposedMenuRepository
import io.ktor.foodies.menu.persistence.MenuRepository
import io.ktor.foodies.menu.stock.RabbitStockEventPublisher
import io.ktor.foodies.menu.stock.stockModule
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitConnectionHealthCheck
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MenuModule(
    val consumers: List<Flow<Unit>>,
    val readinessCheck: HealthCheckRegistry,
    val catalogRepository: MenuRepository,
    val adminRepository: AdminRepository,
)

fun Application.module(config: Config, telemetry: OpenTelemetry): MenuModule {
    val dataSource = dataSource(config.dataSource, telemetry)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()

    val menuRepository = ExposedMenuRepository(dataSource.database)
    val adminRepository = ExposedAdminRepository(dataSource.database)

    val rabbitFactory =
        rabbitConnectionFactory(config.rabbit.host, config.rabbit.port, config.rabbit.username, config.rabbit.password)
    val rabbitConnection = rabbitFactory.newConnection("menu-service")

    val rabbitChannel = rabbitConnection.createChannel()
    rabbitChannel.exchangeDeclare(config.rabbit.exchange, "topic", true)
    val eventPublisher = RabbitStockEventPublisher(Publisher(rabbitChannel, config.rabbit.exchange, Json))
    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        runCatching { rabbitConnection.close() }
    }

    val subscriber = RabbitMQSubscriber(rabbitConnection, config.rabbit.exchange)
    val stock = stockModule(dataSource.database, subscriber, eventPublisher, config.rabbit.queue)

    val readinessCheck = HealthCheckRegistry(Dispatchers.Default) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(rabbitConnection), Duration.ZERO, 5.seconds)
    }

    return MenuModule(
        consumers = stock.consumers,
        readinessCheck = readinessCheck,
        catalogRepository = menuRepository,
        adminRepository = adminRepository,
    )
}
