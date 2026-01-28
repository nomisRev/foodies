package io.ktor.foodies.rabbitmq

import com.rabbitmq.client.ConnectionFactory

fun rabbitConnectionFactory(
    host: String,
    port: Int,
    username: String,
    password: String
): ConnectionFactory = ConnectionFactory().apply {
    this.host = host
    this.port = port
    this.username = username
    this.password = password
}
