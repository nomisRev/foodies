package io.ktor.foodies.server

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.foodies.server.htmx.basket.BasketService
import io.ktor.foodies.server.htmx.basket.HttpBasketService
import io.ktor.foodies.server.htmx.menu.HttpMenuService
import io.ktor.foodies.server.htmx.menu.MenuService
import io.ktor.foodies.server.security.RedisSessionStorage
import io.ktor.foodies.server.telemetry.Monitoring
import io.ktor.foodies.server.telemetry.openTelemetry
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.sessions.SessionStorage
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import kotlinx.coroutines.Dispatchers

data class WebAppModule(
    val menuService: MenuService,
    val basketService: BasketService,
    val httpClient: HttpClient,
    val readinessCheck: HealthCheckRegistry,
    val sessionStorage: SessionStorage
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.module(config: Config, telemetry: OpenTelemetry): WebAppModule {
    val httpClient = HttpClient(Apache5) {
        install(ContentNegotiation) { json() }
        install(KtorClientTelemetry) {
            setOpenTelemetry(telemetry)
        }
    }
    monitor.subscribe(ApplicationStopped) { httpClient.close() }

    val menuService = HttpMenuService(config.menu.baseUrl, httpClient)
    val basketService = HttpBasketService(config.basket.baseUrl, httpClient)

    val auth = if (config.redis.password.isNotBlank()) ":${config.redis.password}@" else ""
    val client = RedisClient.create("redis://$auth${config.redis.host}:${config.redis.port}")
    val connection = client.connect()
    monitor.subscribe(ApplicationStopped) {
        connection.close()
        client.shutdown()
    }

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
        register("basket-service", EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") })
        register("redis", RedisHealthCheck(connection))
    }

    return WebAppModule(
        menuService = menuService,
        basketService = basketService,
        httpClient = httpClient,
        readinessCheck = readinessCheck,
        sessionStorage = RedisSessionStorage(connection.coroutines(), config.redis.ttlSeconds)
    )
}
