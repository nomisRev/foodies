package io.ktor.foodies.menu

import io.ktor.foodies.server.dataSource
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway

data class MenuModule(val menuService: MenuService)

fun Application.module(config: Config): MenuModule {
    val dataSource = dataSource(config.dataSource)
    Flyway.configure()
        .dataSource(dataSource.hikari)
        .load()
        .migrate()
    val menuRepository = ExposedMenuRepository(dataSource.database)
    val menuService = MenuServiceImpl(menuRepository)
    return MenuModule(menuService = menuService)
}