package io.ktor.foodies.menu

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.foodies.server.dataSource
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MenuModule(
    val menuService: MenuService,
    val readinessCheck: HealthCheckRegistry
)

fun Application.module(config: Config): MenuModule {
    val dataSource = dataSource(config.dataSource)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
    val menuRepository = ExposedMenuRepository(dataSource.database)
    val menuService = MenuServiceImpl(menuRepository)

    val readinessCheck = HealthCheckRegistry(Dispatchers.IO) {
        register(HikariConnectionsHealthCheck(dataSource.hikari, 1), Duration.ZERO, 5.seconds)
    }

    return MenuModule(menuService = menuService, readinessCheck = readinessCheck)
}