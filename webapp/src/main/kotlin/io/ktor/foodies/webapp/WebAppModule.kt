package io.ktor.foodies.webapp

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.foodies.webapp.basket.BasketService
import io.ktor.foodies.webapp.basket.HttpBasketService
import io.ktor.foodies.webapp.httpClientModule
import io.ktor.foodies.webapp.menu.HttpMenuService
import io.ktor.foodies.webapp.menu.MenuService
import io.ktor.foodies.webapp.security.securityModule
import io.ktor.server.application.Application
import io.ktor.server.sessions.SessionStorage
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers

data class WebAppModule(
    val menuService: io.ktor.foodies.webapp.menu.MenuService,
    val basketService: io.ktor.foodies.webapp.basket.BasketService,
    val sessionStorage: SessionStorage,
    val httpClient: HttpClient,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: io.ktor.foodies.webapp.Config, telemetry: OpenTelemetry): io.ktor.foodies.webapp.WebAppModule {
    val httpClient = httpClientModule(telemetry)

    val menuService = _root_ide_package_.io.ktor.foodies.webapp.menu.HttpMenuService(config.menu.baseUrl, httpClient)
    val basketService =
        _root_ide_package_.io.ktor.foodies.webapp.basket.HttpBasketService(config.basket.baseUrl, httpClient)

    val security = securityModule(config.redis)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
        register("basket-service", EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") })
        register("redis", RedisHealthCheck(security.redisConnection))
    }

    return _root_ide_package_.io.ktor.foodies.webapp.WebAppModule(
        menuService = menuService,
        basketService = basketService,
        sessionStorage = security.sessionStorage,
        httpClient = httpClient,
        readinessCheck = readinessCheck
    )
}
