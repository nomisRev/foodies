package io.ktor.foodies.server

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.healthcheck.http.EndpointHealthCheck
import com.sksamuel.cohort.lettuce.RedisHealthCheck
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.foodies.server.cart.CartService
import io.ktor.foodies.server.cart.HttpCartService
import io.ktor.foodies.server.menu.HttpMenuService
import io.ktor.foodies.server.menu.MenuService
import io.ktor.foodies.server.security.SecurityModule
import io.ktor.foodies.server.security.securityModule
import io.ktor.foodies.server.shared.httpClientModule
import io.ktor.server.application.Application
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.Dispatchers

data class WebAppModule(
    val menuService: MenuService,
    val cartService: CartService,
    val security: SecurityModule,
    val httpClient: HttpClient,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config, telemetry: OpenTelemetry): WebAppModule {
    val httpClient = httpClientModule(telemetry)

    val menuService = HttpMenuService(config.menu.baseUrl, httpClient)
    val cartService = HttpCartService(config.basket.baseUrl, httpClient)

    val security = securityModule(config.redis)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register("menu-service", EndpointHealthCheck { it.get("${config.menu.baseUrl}/healthz/readiness") })
        register("basket-service", EndpointHealthCheck { it.get("${config.basket.baseUrl}/healthz/readiness") })
        register("redis", RedisHealthCheck(security.redisConnection))
    }

    return WebAppModule(
        menuService = menuService,
        cartService = cartService,
        security = security,
        httpClient = httpClient,
        readinessCheck = readinessCheck
    )
}
