package io.ktor.foodies.server

import com.redis.testcontainers.RedisContainer
import dasniko.testcontainers.keycloak.KeycloakContainer
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import de.infix.testBalloon.framework.shared.TestRegistering
import io.ktor.foodies.server.telemetry.MonitoringConfig
import io.ktor.foodies.server.test.testApplication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.ExternalServicesBuilder
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.future.await
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths

data class ServiceContext(
    val redisContainer: TestFixture<RedisContainer>,
    val redisClient: TestFixture<RedisClient>,
    val keycloakContainer: TestFixture<KeycloakContainer>
)

fun TestSuite.serviceContext(): ServiceContext {
    val fixture = testFixture {
        val redis = RedisContainer("redis:7-alpine")
        val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.5.2").apply {
            val realmFile = Paths.get("../k8s/base/keycloak/realm-common.json").toAbsolutePath().normalize()
            withCopyFileToContainer(MountableFile.forHostPath(realmFile), "/opt/keycloak/data/import/realm.json")
        }
        Startables.deepStart(redis, keycloak).await()
        keycloak.apply {
            val clients = keycloakAdminClient.realm("foodies-keycloak").clients()
            val existingClient = clients.findByClientId("foodies").firstOrNull()
                ?: error("Expected Keycloak client 'foodies' from realm import.")
            existingClient.redirectUris = listOf("http://localhost:8080/oauth/callback")
            existingClient.webOrigins = listOf("http://localhost")
            existingClient.attributes = mapOf("post.logout.redirect.uris" to "http://localhost/*")
            clients[existingClient.id].update(existingClient)
        }
        Pair(redis, keycloak)
    }
    val redisContainer = testFixture { fixture().first }
    val keycloakContainer = testFixture { fixture().second }
    val redisClient = testFixture { RedisClient.create(fixture().first.redisURI) }
    return ServiceContext(redisContainer, redisClient, keycloakContainer)
}

fun ExternalServicesBuilder.readiness(port: String) =
    hosts("http://localhost:$port") {
        routing {
            get("/healthz/readiness") { call.respondText("OK") }
        }
    }

@OptIn(ExperimentalLettuceCoroutinesApi::class)
@TestRegistering
context(ctx: ServiceContext)
fun TestSuite.testWebAppService(
    name: String,
    block: suspend context(Test.ExecutionScope) ApplicationTestBuilder.() -> Unit
) = testApplication(name) {
    externalServices {
        readiness("8081")
        readiness("8082")
    }
    val keycloak = ctx.keycloakContainer()
    val config = Config(
        host = "0.0.0.0",
        port = 8080,
        security = Config.Security(
            issuer = "${keycloak.authServerUrl}/realms/foodies-keycloak",
            clientId = "foodies",
            clientSecret = "foodies_client_secret"
        ),
        menu = Config.Menu(baseUrl = "http://localhost:8081"),
        basket = Config.Basket(baseUrl = "http://localhost:8082"),
        redis = Config.RedisSession(
            host = ctx.redisContainer().host,
            port = ctx.redisContainer().firstMappedPort,
            password = "",
            ttlSeconds = 3600
        ),
        telemetry = MonitoringConfig(otlpEndpoint = "http://localhost:4317")
    )
    application { app(config, module(config, OpenTelemetry.noop())) }
    block()
}
