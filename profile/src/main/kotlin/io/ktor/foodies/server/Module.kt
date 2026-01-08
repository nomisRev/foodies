package io.ktor.foodies.server

import com.rabbitmq.client.ConnectionFactory
import io.ktor.foodies.server.consumers.Consumer
import io.ktor.foodies.server.consumers.NewUserEvent
import io.ktor.foodies.server.consumers.channel
import io.ktor.foodies.server.consumers.messages
import io.ktor.foodies.server.consumers.newUserConsumer
import io.ktor.foodies.server.profile.ExposedProfileRepository
import io.ktor.server.application.Application
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
        newUserConsumer(
            connectionFactory.channel(config.rabbit.queue).messages<NewUserEvent>(config.rabbit.queue),
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
