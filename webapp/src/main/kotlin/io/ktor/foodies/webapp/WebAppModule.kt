package io.ktor.foodies.webapp

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.foodies.basket.routes.BasketClient
import io.ktor.foodies.webapp.httpClientModule
import io.ktor.foodies.webapp.menu.HttpMenuService
import io.ktor.foodies.webapp.menu.MenuService
import io.ktor.foodies.webapp.security.securityModule
import io.ktor.server.application.Application
import io.ktor.server.sessions.SessionStorage
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers

data class WebAppModule(
    val menuService: MenuService,
    val basketService: BasketClient,
    val sessionStorage: SessionStorage,
    val httpClient: HttpClient,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config, telemetry: OpenTelemetry): WebAppModule {
    val httpClient = httpClientModule(telemetry)

    val menuService = HttpMenuService(config.menu.baseUrl, httpClient)
    val basketService = BasketClient(httpClient)

    val security = securityModule(config.redis)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
        register("basket-service", EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") })
        register("redis", RedisHealthCheck(security.redisConnection))
    }

    return WebAppModule(
        menuService = menuService,
        basketService = basketService,
        sessionStorage = security.sessionStorage,
        httpClient = httpClient,
        readinessCheck = readinessCheck
    )
}
