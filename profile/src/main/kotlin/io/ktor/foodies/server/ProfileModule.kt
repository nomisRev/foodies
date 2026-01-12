package io.ktor.foodies.server

import io.ktor.foodies.rabbitmq.Consumer
import io.ktor.foodies.rabbitmq.RabbitConfig
import io.ktor.foodies.rabbitmq.channel
import io.ktor.foodies.rabbitmq.messages
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.consumers.userEventConsumer
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.foodies.user.event.UserEvent
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway

class ProfileModule(val consumers: List<Consumer>)

fun Application.module(config: Config): ProfileModule {
    val dataSource = dataSource(config.dataSource)
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
    val newUserConsumer =
        userEventConsumer(
            connectionFactory.channel(config.rabbit.queue, "profile-service")
                .messages<UserEvent>(config.rabbit.queue),
            profileRepository
        )

    return ProfileModule(listOf(newUserConsumer))
}