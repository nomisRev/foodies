package io.ktor.foodies.payment

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import com.sksamuel.cohort.HealthCheckRegistry
import io.ktor.foodies.events.order.OrderStockConfirmedEvent
import io.ktor.foodies.payment.events.RabbitMQEventPublisher
import io.ktor.foodies.payment.events.orderStockConfirmedEventConsumer
import io.ktor.foodies.payment.gateway.SimulatedPaymentGateway
import io.ktor.foodies.rabbitmq.Publisher
import io.ktor.foodies.rabbitmq.RabbitMQSubscriber
import io.ktor.foodies.rabbitmq.rabbitConnectionFactory
import io.ktor.foodies.rabbitmq.subscribe
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

data class ServiceContext(
    val postgresContainer: TestSuite.Fixture<PostgreSQLContainer>,
    val database: TestSuite.Fixture<Database>,
    val rabbitContainer: TestSuite.Fixture<RabbitContainer>
)

fun TestSuite.serviceContext(): ServiceContext {
    val container = testFixture {
        PostgreSQLContainer().apply { start() }
    }

    val database = testFixture {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = container().jdbcUrl
            username = container().username
            password = container().password
        })

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        Database.connect(ds)
    }

    val rabbit = rabbitContainer()

    return ServiceContext(container, database, rabbit)
}

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testPostgres(
    name: String,
    block: suspend context(TestExecutionScope) (repository: PostgresPaymentRepository) -> Unit
) = test(name) {
    block(PostgresPaymentRepository(ctx.database()))
}

data class PaymentTestModule(
    val paymentService: PaymentService,
    val paymentRepository: PaymentRepository,
    val eventPublisher: RabbitMQEventPublisher
)

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testPaymentService(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.(module: PaymentTestModule) -> Unit
) {
    testApplication(name) {
        val paymentRepository = PostgresPaymentRepository(ctx.database())
        val gatewayConfig = PaymentGatewayConfig(alwaysSucceed = true, processingDelayMs = 0)
        val paymentGateway = SimulatedPaymentGateway(gatewayConfig)
        val paymentService = PaymentServiceImpl(paymentRepository, paymentGateway)

        val rabbitConfig = RabbitConfig(
            host = ctx.rabbitContainer().host,
            port = ctx.rabbitContainer().amqpPort,
            username = ctx.rabbitContainer().adminUsername,
            password = ctx.rabbitContainer().adminPassword,
            consumeQueue = "test.order.stock.confirmed",
            publishExchange = "test.payment.events"
        )

        val connectionFactory = rabbitConnectionFactory(
            rabbitConfig.host,
            rabbitConfig.port,
            rabbitConfig.username,
            rabbitConfig.password
        )
        val connection = connectionFactory.newConnection()
        val channel = connection.createChannel()
        channel.exchangeDeclare(rabbitConfig.publishExchange, "topic", true)
        val eventPublisher = RabbitMQEventPublisher(Publisher(channel, rabbitConfig.publishExchange, Json))
        val subscriber = RabbitMQSubscriber(connection, rabbitConfig.publishExchange)
        val consumer = orderStockConfirmedEventConsumer(
            subscriber.subscribe<OrderStockConfirmedEvent>(rabbitConfig.consumeQueue),
            paymentService,
            eventPublisher
        )

        val module = PaymentModule(
            paymentService = paymentService,
            consumers = listOf(consumer),
            eventPublisher = eventPublisher,
            readinessCheck = HealthCheckRegistry(Dispatchers.IO)
        )

        application {
            app(module)
        }

        block(PaymentTestModule(paymentService, paymentRepository, eventPublisher))
    }
}
