package io.ktor.foodies.menu

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.menu.events.MenuEventConsumer
import io.ktor.foodies.menu.events.RabbitMenuEventPublisher
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import io.ktor.server.application.ApplicationStopped
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class
MenuModule(
    val menuService: MenuService,
    val startupCheck: HealthCheckRegistry,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config): MenuModule {
    val dataSource = dataSource(config.dataSource)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
    val menuRepository = ExposedMenuRepository(dataSource.database)
    val menuService = MenuServiceImpl(menuRepository)

    // RabbitMQ Setup
    val rabbitFactory = rabbitConnectionFactory(
        io.ktor.foodies.rabbitmq.RabbitConfig(
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

    val eventPublisher = RabbitMenuEventPublisher(rabbitChannel, config.rabbit.exchange)
    val eventConsumer = MenuEventConsumer(
        rabbitChannel,
        config.rabbit.exchange,
        config.rabbit.queue,
        menuService,
        eventPublisher,
        this
    )
    eventConsumer.start()

    val startupCheck = HealthCheckRegistry(Dispatchers.Default) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(rabbitConnection), Duration.ZERO, 5.seconds)
    }

    val readinessCheck = HealthCheckRegistry(Dispatchers.Default) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(rabbitConnection), Duration.ZERO, 5.seconds)
    }

    return MenuModule(
        menuService = menuService,
        startupCheck = startupCheck,
        readinessCheck = readinessCheck
    )
}