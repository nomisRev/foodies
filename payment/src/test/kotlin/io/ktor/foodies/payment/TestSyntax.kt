package io.ktor.foodies.payment

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.payment.persistence.ExposedPaymentRepository
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import io.opentelemetry.api.OpenTelemetry
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

data class ServiceContext(
    val postgres: TestFixture<PostgreSQLContainer>,
    val rabbit: TestFixture<RabbitContainer>,
)

fun TestSuite.serviceContext(): ServiceContext {
    val postgres = postgresContainer()
    val rabbit = rabbitContainer()
    return ServiceContext(postgres, rabbit)
}

fun testConfig(
    postgres: PostgreSQLContainer,
    rabbit: RabbitContainer,
) = Config(
    host = "localhost",
    port = 8085,
    dataSource = DataSource.Config(
        url = postgres.jdbcUrl,
        username = postgres.username,
        password = postgres.password,
    ),
    rabbit = RabbitConfig(
        host = rabbit.host,
        port = rabbit.amqpPort,
        username = rabbit.adminUsername,
        password = rabbit.adminPassword,
        consumeQueue = "test.payment.consume",
        publishExchange = "test.payment.events",
    ),
    telemetry = Config.Telemetry(otlpEndpoint = "http://localhost:4317"),
)

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testPostgres(
    name: String,
    block: suspend context(Test.ExecutionScope) (ExposedPaymentRepository) -> Unit,
) {
    test(name) {
        val hikari = HikariDataSource(HikariConfig().apply {
            jdbcUrl = ctx.postgres().jdbcUrl
            username = ctx.postgres().username
            password = ctx.postgres().password
        })
        Flyway.configure().dataSource(hikari).load().migrate()
        val database = Database.connect(hikari)
        val repository = ExposedPaymentRepository(database)
        block(repository)
        hikari.close()
    }
}

data class PaymentTestModule(
    val module: PaymentModule,
    val rabbit: RabbitContainer,
)

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testPaymentService(
    name: String,
    block: suspend context(Test.ExecutionScope) ApplicationTestBuilder.(PaymentTestModule) -> Unit,
) {
    testApplication(name) {
        var paymentModule: PaymentModule? = null
        application {
            val config = testConfig(ctx.postgres(), ctx.rabbit())
            paymentModule = module(config, OpenTelemetry.noop())
            app(paymentModule)
        }
        startApplication()
        block(PaymentTestModule(paymentModule!!, ctx.rabbit()))
    }
}
