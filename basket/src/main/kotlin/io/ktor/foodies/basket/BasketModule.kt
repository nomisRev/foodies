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
import io.ktor.foodies.rabbitmq.RabbitConnectionHealthCheck
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines
import io.opentelemetry.api.OpenTelemetry
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
fun Application.module(config: Config, telemetry: OpenTelemetry): BasketModule {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        install(KtorClientTelemetry) {
            setOpenTelemetry(telemetry)
        }
    }
    monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val menuClient = HttpMenuClient(httpClient, config.menu.baseUrl)

    val (redisClient, redisCommands) = createRedisClient(config.redis)
    monitor.subscribe(ApplicationStopped) { redisClient.shutdown() }

    val basketRepository = RedisBasketRepository(redisCommands.coroutines())
    val basketService = BasketServiceImpl(basketRepository, menuClient)

    val connectionFactory =
        rabbitConnectionFactory(config.rabbit.host, config.rabbit.port, config.rabbit.username, config.rabbit.password)
    val connection = connectionFactory.newConnection("basket-service")
    monitor.subscribe(ApplicationStopped) { connection.close() }

    val subscriber = RabbitMQSubscriber(connection, "foodies")
    val orderCreatedConsumer = orderCreatedEventConsumer(
        subscriber.subscribe<OrderCreatedEvent>(config.rabbit.queue),
        basketRepository
    )

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(RedisHealthCheck(redisCommands), Duration.ZERO, 5.seconds)
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
        register(RabbitConnectionHealthCheck(connection), Duration.ZERO, 5.seconds)
    }

    return BasketModule(
        basketService = basketService,
        consumers = listOf(orderCreatedConsumer),
        readinessCheck = readinessCheck
    )
}
