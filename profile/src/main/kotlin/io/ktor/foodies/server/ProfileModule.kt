package io.ktor.foodies.server

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.rabbitmq.RabbitConnectionHealthCheck
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.consumers.userEventConsumer
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.events.user.UserEvent
import io.ktor.foodies.server.telemetry.Monitoring
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ProfileModule(
    val consumers: List<Flow<Unit>>,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config, telemetry: OpenTelemetry): ProfileModule {
    val dataSource = dataSource(config.dataSource, telemetry)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
    val profileRepository = ExposedProfileRepository(dataSource.database)

    val connectionFactory =
        rabbitConnectionFactory(config.rabbit.host, config.rabbit.port, config.rabbit.username, config.rabbit.password)
    val connection = connectionFactory.newConnection("profile-service")

    monitor.subscribe(ApplicationStopped) { connection.close() }
    val subscriber = RabbitMQSubscriber(connection, "foodies")

    val newUserConsumer =
        userEventConsumer(subscriber.subscribe<UserEvent>(config.rabbit.queue), profileRepository)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
        register(RabbitConnectionHealthCheck(connection), Duration.ZERO, 5.seconds)
    }

    return ProfileModule(consumers = listOf(newUserConsumer), readinessCheck = readinessCheck)
}