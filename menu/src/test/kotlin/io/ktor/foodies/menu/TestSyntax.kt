package io.ktor.foodies.menu

import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.openid.Auth
import io.ktor.foodies.server.openid.security
import io.ktor.foodies.server.telemetry.MonitoringConfig
import io.ktor.foodies.server.test.KeycloakAuthContext
import io.ktor.foodies.server.test.KeycloakContainer
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.dataSource
import io.ktor.foodies.server.test.keycloakContainer
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    val rabbitContainer: TestSuite.Fixture<RabbitContainer>,
    val keycloakContainer: TestSuite.Fixture<KeycloakContainer>,
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
    val rabbitContainer = rabbitContainer()
    val keycloak = keycloakContainer()
    return ServiceContext(container, rabbitContainer, keycloak, ds, service)
}

data class MenuTestModule(
    val menuService: MenuService,
    val authContext: KeycloakAuthContext
)

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testMenuService(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.(module: MenuTestModule) -> Unit
) {
    testApplication(name) {
        val auth = Auth(
            issuer = "${ctx.keycloakContainer().authServerUrl}/realms/foodies-keycloak",
            serviceAudience = "menu-service",
            userAudience = "foodies"
        )
        val config = Config(
            host = "0.0.0.0",
            port = 8080,
            auth = auth,
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
        )
        val module = MenuModule(
            config = config,
            menuService = ctx.menuService(),
            consumers = emptyList(),
            readinessCheck = com.sksamuel.cohort.HealthCheckRegistry(Dispatchers.Default)
        )
        val client = HttpClient(Apache5) {
            install(ContentNegotiation) { json() }
        }
        application {
            runBlocking { security(auth, client) }
            app(module)
        }
        block(MenuTestModule(module.menuService, KeycloakAuthContext(ctx.keycloakContainer(), client)))
    }
}
