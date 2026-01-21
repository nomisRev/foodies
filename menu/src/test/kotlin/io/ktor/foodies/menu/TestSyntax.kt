package io.ktor.foodies.menu

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.infix.testBalloon.framework.core.TestExecutionScope
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.DataSource
import io.ktor.foodies.server.openid.Auth
import io.ktor.foodies.server.telemetry.MonitoringConfig
import io.ktor.foodies.server.test.PostgreSQLContainer
import io.ktor.foodies.server.test.RabbitContainer
import io.ktor.foodies.server.test.dataSource
import io.ktor.foodies.server.test.postgresContainer
import io.ktor.foodies.server.test.rabbitContainer
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.opentelemetry.api.OpenTelemetry
import org.flywaydb.core.Flyway
import java.util.Date

private const val TEST_SECRET = "test-jwt-secret-for-end-to-end-testing"
private const val TEST_ISSUER = "test-issuer"
private const val TEST_AUDIENCE = "test-audience"

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

fun createTestToken(
    subject: String,
    issuer: String = TEST_ISSUER,
    audience: String = TEST_AUDIENCE,
    roles: List<String> = listOf("admin")
): String = JWT.create()
    .withSubject(subject)
    .withIssuer(issuer)
    .withAudience(audience)
    .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
    .withClaim("realm_access", mapOf("roles" to roles))
    .sign(Algorithm.HMAC256(TEST_SECRET))

@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testMenuService(
    name: String,
    block: suspend context(TestExecutionScope) ApplicationTestBuilder.() -> Unit
) {
    testApplication(name) {
        application {
            install(Authentication) {
                jwt {
                    verifier(
                        JWT.require(Algorithm.HMAC256(TEST_SECRET))
                            .withIssuer(TEST_ISSUER)
                            .withAudience(TEST_AUDIENCE)
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            app(
                module(
                    Config(
                        host = "0.0.0.0",
                        port = 8080,
                        auth = Auth(issuer = "http://localhost:9090/realms/foodies-keycloak"),
                        dataSource = ctx.container().config(),
                        rabbit = RabbitConfig(
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
