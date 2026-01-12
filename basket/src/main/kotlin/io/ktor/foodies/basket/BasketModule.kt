package io.ktor.foodies.basket

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.basket.events.OrderCreatedEvent
import io.ktor.foodies.basket.events.orderCreatedEventConsumer
import io.ktor.foodies.rabbitmq.Consumer
import io.ktor.foodies.rabbitmq.RabbitConfig
import io.ktor.foodies.rabbitmq.channel
import io.ktor.foodies.rabbitmq.messages
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.lettuce.core.ExperimentalLettuceCoroutinesApi

@OptIn(ExperimentalLettuceCoroutinesApi::class)
data class BasketModule(
    val basketService: BasketService,
    val consumers: List<Consumer>
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.module(config: Config): BasketModule {
    // Create HTTP client for Menu service
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }
    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }

    // Create Menu client
    val menuClient = HttpMenuClient(httpClient, config.menu.baseUrl)

    // Create Redis client
    val (redisClient, redisCommands) = createRedisClient(config.redis)
    monitor.subscribe(ApplicationStopped) {
        redisClient.shutdown()
    }

    // Create repository and service
    val basketRepository = RedisBasketRepository(redisCommands)
    val basketService = BasketServiceImpl(basketRepository, menuClient)

    // Create RabbitMQ consumer for OrderCreatedEvent
    val rabbitConfig = RabbitConfig(
        host = config.rabbit.host,
        port = config.rabbit.port,
        username = config.rabbit.username,
        password = config.rabbit.password
    )
    val connectionFactory = rabbitConnectionFactory(rabbitConfig)
    val orderCreatedConsumer = orderCreatedEventConsumer(
        connectionFactory.channel(config.rabbit.queue, "basket-service")
            .messages<OrderCreatedEvent>(config.rabbit.queue),
        basketRepository
    )

    return BasketModule(
        basketService = basketService,
        consumers = listOf(orderCreatedConsumer)
    )
}
