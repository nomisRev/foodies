package io.ktor.foodies.basket

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.foodies.basket.events.OrderCreatedEvent
import io.ktor.foodies.basket.events.orderCreatedEventConsumer
import io.ktor.foodies.rabbitmq.Consumer
import io.ktor.foodies.rabbitmq.RabbitConfig
import io.ktor.foodies.rabbitmq.channel
import io.ktor.foodies.rabbitmq.messages
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.server.telemetry.openTelemetry
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalLettuceCoroutinesApi::class)
data class BasketModule(
    val basketService: BasketService,
    val consumers: List<Consumer>,
    val readinessCheck: HealthCheckRegistry
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.module(config: Config): BasketModule {
    val openTelemetry = openTelemetry(name = "basket-service", version = VERSION)
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        install(KtorClientTelemetry) {
            setOpenTelemetry(openTelemetry)
        }
    }
    monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val menuClient = HttpMenuClient(httpClient, config.menu.baseUrl)

    val (redisClient, redisCommands) = createRedisClient(config.redis)
    monitor.subscribe(ApplicationStopped) { redisClient.shutdown() }

    val basketRepository = RedisBasketRepository(redisCommands.coroutines())
    val basketService = BasketServiceImpl(basketRepository, menuClient)

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

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(RedisHealthCheck(redisCommands), Duration.ZERO, 5.seconds)
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
    }

    return BasketModule(
        basketService = basketService,
        consumers = listOf(orderCreatedConsumer),
        readinessCheck = readinessCheck
    )
}
