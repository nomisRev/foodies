package io.ktor.foodies.menu

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.telemetry.MonitoringConfig
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.dataSource
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.opentelemetry.api.OpenTelemetry
import org.flywaydb.core.Flyway
import java.time.Instant
import java.util.Date

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
    return ServiceContext(container, rabbitContainer, ds, service)
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

fun generateJwtToken(
    subject: String = "test-service",
    audience: String = "menu-service",
    secret: String = "super-secret-key-that-should-be-in-config",
    expirationOffsetSeconds: Long = 3600
): String {
    val now = Instant.now()
    return JWT.create()
        .withIssuer("sap.foodies.com")
        .withSubject(subject)
        .withAudience(audience)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plusSeconds(expirationOffsetSeconds)))
        .sign(Algorithm.HMAC256(secret))
}

fun ApplicationTestBuilder.authorizedJsonClient(token: String? = null): HttpClient {
    return createClient {
        install(ContentNegotiation) { json() }
        if (token != null) {
            defaultRequest {
                header("Authorization", "Bearer $token")
            }
        }
    }
}

