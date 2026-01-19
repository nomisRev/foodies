package io.ktor.foodies.payment

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.events.order.OrderStockConfirmedEvent
import io.ktor.foodies.payment.events.RabbitMQEventPublisher
import io.ktor.foodies.payment.events.orderStockConfirmedEventConsumer
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitConnectionHealthCheck
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.*
import io.opentelemetry.api.OpenTelemetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway

class PaymentModule(
    val paymentService: PaymentService,
    val consumers: List<Flow<Unit>>,
    val eventPublisher: RabbitMQEventPublisher,
    val readinessCheck: HealthCheckRegistry,
)

fun Application.module(config: Config, telemetry: OpenTelemetry): PaymentModule {
    val dataSource = dataSource(config.dataSource, telemetry)

    Flyway.configure().dataSource(dataSource.hikari).load().migrate()

    val paymentRepository = PostgresPaymentRepository(dataSource.database)
    val paymentGateway = SimulatedPaymentGateway(config.gateway)
    val paymentService = PaymentServiceImpl(paymentRepository, paymentGateway)

    val connectionFactory =
        rabbitConnectionFactory(
            config.rabbit.host,
            config.rabbit.port,
            config.rabbit.username,
            config.rabbit.password,
        )
    val connection = connectionFactory.newConnection("payment-service")

    val rabbitChannel = connection.createChannel()
    rabbitChannel.exchangeDeclare(config.rabbit.publishExchange, "topic", true)
    val eventPublisher =
        RabbitMQEventPublisher(Publisher(rabbitChannel, config.rabbit.publishExchange, Json))
    val subscriber = RabbitMQSubscriber(connection, config.rabbit.publishExchange)

    val eventConsumer =
        orderStockConfirmedEventConsumer(
            subscriber.subscribe<OrderStockConfirmedEvent>(config.rabbit.consumeQueue),
            paymentService,
            eventPublisher,
        )

    monitor.subscribe(ApplicationStopped) {
        runCatching { rabbitChannel.close() }
        connection.close()
    }

    val readinessCheck =
        HealthCheckRegistry(Dispatchers.IO) {
            register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
            register(RabbitConnectionHealthCheck(connection), Duration.ZERO, 5.seconds)
        }

    return PaymentModule(
        paymentService = paymentService,
        consumers = listOf(eventConsumer),
        eventPublisher = eventPublisher,
        readinessCheck = readinessCheck,
    )
}
