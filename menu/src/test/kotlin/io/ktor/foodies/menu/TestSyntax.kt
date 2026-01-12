package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.dataSource
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import org.flywaydb.core.Flyway

fun TestSuite.migratedMenuDataSource(): TestSuite.Fixture<DataSource> =
    testFixture {
        val container = postgresContainer()()
        val dataSource = container.dataSource()()
        Flyway.configure().dataSource(dataSource.hikari).load().migrate()
        dataSource
    }

data class ServiceContext(
    val container: TestSuite.Fixture<PostgreSQLContainer>,
    val dataSource: TestSuite.Fixture<DataSource>,
    val menuService: TestSuite.Fixture<MenuService>
)

fun TestSuite.serviceContext(): ServiceContext {
    val container = postgresContainer()
    val ds = testFixture { container().dataSource()() }
    val service = testFixture<MenuService> {
        val repository = ExposedMenuRepository(ds().database)
        MenuServiceImpl(repository)
    }
    return ServiceContext(container, ds, service)
}

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testMenuService(
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
                        ctx.container().config(),
                    )
                )
            )
        }
        block()
    }
}
