package io.ktor.foodies.server

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.foodies.server.cart.CartService
import io.ktor.foodies.server.cart.cartModule
import io.ktor.foodies.server.menu.MenuService
import io.ktor.foodies.server.menu.menuModule
import io.ktor.foodies.server.security.securityModule
import io.ktor.foodies.server.shared.httpClientModule
import io.ktor.server.application.Application
import io.ktor.server.sessions.SessionStorage
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers

data class WebAppModule(
    val menuService: MenuService,
    val cartService: CartService,
    val httpClient: HttpClient,
    val readinessCheck: HealthCheckRegistry,
    val sessionStorage: SessionStorage
)

fun Application.module(config: Config, telemetry: OpenTelemetry): WebAppModule {
    val httpClient = httpClientModule(telemetry)

    val menu = menuModule(config.menu, httpClient)
    val cart = cartModule(config.basket, httpClient)

    val security = securityModule(config.redis)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
        register("basket-service", EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") })
        register("redis", RedisHealthCheck(security.redisConnection))
    }

    return WebAppModule(
        menuService = menu.menuService,
        cartService = cart.cartService,
        httpClient = httpClient,
        readinessCheck = readinessCheck,
        sessionStorage = security.sessionStorage
    )
}
