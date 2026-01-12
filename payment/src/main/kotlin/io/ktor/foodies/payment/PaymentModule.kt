package io.ktor.foodies.payment

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.ktor.foodies.payment.events.OrderStockConfirmedEventHandler
import io.ktor.foodies.payment.events.RabbitMQEventConsumer
import io.ktor.foodies.payment.events.RabbitMQEventPublisher
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PaymentModule(
    val paymentService: PaymentService,
    val eventConsumer: RabbitMQEventConsumer,
    val eventPublisher: RabbitMQEventPublisher,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config): PaymentModule {
    val dataSource = dataSource(config.dataSource)

    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()

    val paymentRepository = PostgresPaymentRepository(dataSource.database)
    val paymentGateway = SimulatedPaymentGateway(config.gateway)
    val paymentService = PaymentServiceImpl(paymentRepository, paymentGateway)
    val eventPublisher = RabbitMQEventPublisher(config.rabbit)
    val eventHandler = OrderStockConfirmedEventHandler(paymentService, eventPublisher)
    val eventConsumer = RabbitMQEventConsumer(config.rabbit, eventHandler)

    monitor.subscribe(ApplicationStopped) {
        eventConsumer.close()
        eventPublisher.close()
    }

    eventConsumer.start()

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
    }

    return PaymentModule(
        paymentService = paymentService,
        eventConsumer = eventConsumer,
        eventPublisher = eventPublisher,
        readinessCheck = readinessCheck
    )
}
