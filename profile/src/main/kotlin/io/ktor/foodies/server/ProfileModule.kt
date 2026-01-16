package io.ktor.foodies.server

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.rabbitmq.Consumer
import io.ktor.foodies.rabbitmq.RabbitConfig
import io.ktor.foodies.rabbitmq.channel
import io.ktor.foodies.rabbitmq.messages
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.consumers.userEventConsumer
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.user.event.UserEvent
import io.ktor.server.application.Application
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ProfileModule(
    val consumers: List<Consumer>,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config, telemetry: OpenTelemetry): ProfileModule {
    val dataSource = dataSource(config.dataSource, telemetry)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
    val profileRepository = ExposedProfileRepository(dataSource.database)

    val rabbitConfig = RabbitConfig(
        host = config.rabbit.host,
        port = config.rabbit.port,
        username = config.rabbit.username,
        password = config.rabbit.password
    )
    val connectionFactory = rabbitConnectionFactory(rabbitConfig)
    val connection = connectionFactory.newConnection()
    val newUserConsumer =
        userEventConsumer(
            connectionFactory.channel(config.rabbit.queue, "profile-service")
                .messages<UserEvent>(config.rabbit.queue),
            profileRepository
        )

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(connection), Duration.ZERO, 5.seconds)
    }

    return ProfileModule(
        consumers = listOf(newUserConsumer),
        readinessCheck = readinessCheck
    )
}