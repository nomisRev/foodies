package io.ktor.foodies.server

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import io.ktor.foodies.server.consumers.Consumer
import io.ktor.foodies.server.consumers.messages
import io.ktor.foodies.server.consumers.userEventConsumer
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.user.event.UserEvent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import org.flywaydb.core.Flyway

class Module(val consumers: List<Consumer>)

fun Application.module(config: Config): Module {
    val dataSource = dataSource(config.dataSource)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
    val profileRepository = ExposedProfileRepository(dataSource.database)

    val connectionFactory = rabbitConnectionFactory(config)
    val newUserConsumer =
        userEventConsumer(
            connectionFactory.channel(config.rabbit.queue).messages<UserEvent>(config.rabbit.queue),
            profileRepository
        )

    return Module(listOf(newUserConsumer))
}

private fun rabbitConnectionFactory(config: Config): ConnectionFactory = ConnectionFactory().apply {
    host = config.rabbit.host
    port = config.rabbit.port
    username = config.rabbit.username
    password = config.rabbit.password
}

private const val CONSUMER_CONNECTION_NAME = "profile-service"

context(application: Application)
private fun ConnectionFactory.channel(queueName: String): Channel {
    val connection = newConnection(CONSUMER_CONNECTION_NAME)
    val channel = connection.createChannel().apply {
        queueDeclare(queueName, true, false, false, null)
    }
    application.monitor.subscribe(ApplicationStopped) {
        val logger = application.environment.log
        runCatching { channel.close() }.onFailure { logger.warn("Failed to close RabbitMQ channel", it) }
        runCatching { connection.close() }.onFailure { logger.warn("Failed to close RabbitMQ connection", it) }
    }
    return channel
}