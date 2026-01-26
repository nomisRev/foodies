package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.telemetry.MonitoringConfig
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.dataSource
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import io.opentelemetry.api.OpenTelemetry
import org.flywaydb.core.Flyway

fun TestSuite.migratedMenuDataSource(): TestFixture<DataSource> =
    testFixture {
        val container = postgresContainer()()
        val dataSource = container.dataSource()()
        Flyway.configure().dataSource(dataSource.hikari).load().migrate()
        dataSource
    }

data class ServiceContext(
    val container: TestFixture<PostgreSQLContainer>,
    val rabbitContainer: TestFixture<RabbitContainer>,
    val dataSource: TestFixture<DataSource>,
    val menuService: TestFixture<MenuService>
)

fun TestSuite.serviceContext(): ServiceContext {
    val container = postgresContainer()
    val ds = testFixture { container().dataSource()() }
    val service = testFixture<MenuService> {
        val repository = ExposedMenuRepository(ds().database)
        MenuServiceImpl(repository)
    }
    val rabbitContainer = rabbitContainer()
    return ServiceContext(container, rabbitContainer, ds, service)
}

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testMenuService(
    name: String,
    block: suspend context(Test.ExecutionScope) ApplicationTestBuilder.() -> Unit
) {
    testApplication(name) {
        application {
            app(
                module(
                    Config(
                        host = "0.0.0.0",
                        port = 8080,
                        ctx.container().config(),
                        RabbitConfig(
                            ctx.rabbitContainer().host,
                            ctx.rabbitContainer().amqpPort,
                            ctx.rabbitContainer().adminUsername,
                            ctx.rabbitContainer().adminPassword,
                        ),
                        telemetry = MonitoringConfig(
                            otlpEndpoint = "http://localhost:4317"
                        )
                    ),
                    OpenTelemetry.noop()
                )
            )
        }
        block()
    }
}
