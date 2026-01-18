package io.ktor.foodies.server

import com.rabbitmq.client.ConnectionFactory
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.dataSource
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import io.opentelemetry.api.OpenTelemetry

data class ServiceContext(
    val postgreSQLContainer: TestSuite.Fixture<PostgreSQLContainer>,
    val dataSource: TestSuite.Fixture<DataSource>,
    val rabbitContainer: TestSuite.Fixture<RabbitContainer>,
    val connectionFactory: TestSuite.Fixture<ConnectionFactory>,
)

fun TestSuite.serviceContext(): ServiceContext {
    val container = postgresContainer()
    val dataSource = testFixture {
        val dataSource = container().dataSource()()
        dataSource
    }
    val rabbitContainer = rabbitContainer()
    val connectionFactory = testFixture { rabbitContainer().connectionFactory() }
    return ServiceContext(container, dataSource, rabbitContainer, connectionFactory)
}

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testProfileService(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.() -> Unit
) {
    testApplication(name) {
        application {
            app(
                module(
                    Config(
                        host = "0.0.0.0",
                        port = 8080,
                        dataSource = DataSource.Config(
                            ctx.postgreSQLContainer().jdbcUrl,
                            ctx.postgreSQLContainer().username,
                            ctx.postgreSQLContainer().password
                        ),
                        rabbit = Config.Rabbit(
                            ctx.rabbitContainer().host,
                            ctx.rabbitContainer().amqpPort,
                            ctx.rabbitContainer().adminUsername,
                            ctx.rabbitContainer().adminPassword,
                            "profile-queue"
                        ),
                        telemetry = Config.Telemetry("http://localhost:4317")
                    ),
                    OpenTelemetry.noop()
                )
            )
        }
        block()
    }
}
