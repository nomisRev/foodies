package io.ktor.foodies.menu

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.menu.events.RabbitMenuEventPublisher
import io.ktor.foodies.menu.events.processOrderAwaitingValidationConsumer
import io.ktor.foodies.menu.events.processStockReturnedConsumer
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.Application
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.flow.launchIn
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MenuModule(
    val menuService: MenuService,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config, telemetry: OpenTelemetry): MenuModule {
    val dataSource = dataSource(config.dataSource, telemetry)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()

    val menuRepository = ExposedMenuRepository(dataSource.database)
    val menuService = MenuServiceImpl(menuRepository)

    val rabbitFactory =
        rabbitConnectionFactory(config.rabbit.host, config.rabbit.port, config.rabbit.username, config.rabbit.password)
    val rabbitConnection = rabbitFactory.newConnection("menu-service")

    val rabbitChannel = rabbitConnection.createChannel()
    rabbitChannel.exchangeDeclare(config.rabbit.exchange, "topic", true)
    val eventPublisher = RabbitMenuEventPublisher(rabbitChannel, config.rabbit.exchange)
    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        runCatching { rabbitConnection.close() }
    }

    val subscriber = RabbitMQSubscriber(rabbitConnection, config.rabbit.exchange)
    subscriber.processOrderAwaitingValidationConsumer(config.rabbit.queue, menuService, eventPublisher).launchIn(this)
    subscriber.processStockReturnedConsumer("menu.stock-returned", menuService).launchIn(this)

    val readinessCheck = HealthCheckRegistry(Dispatchers.Default) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(rabbitConnection), Duration.ZERO, 5.seconds)
    }

    return MenuModule(
        menuService = menuService,
        readinessCheck = readinessCheck
    )
}