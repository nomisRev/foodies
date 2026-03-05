package io.ktor.foodies.server.menu

import io.ktor.client.HttpClient
import io.ktor.foodies.server.Config
import io.ktor.server.application.Application

data class MenuModule(val menuService: MenuService)

fun Application.menuModule(config: Config.Menu, httpClient: HttpClient): MenuModule {
    val menuService = HttpMenuService(config.baseUrl, httpClient)
    menuRoutes(menuService)
    return MenuModule(menuService = menuService)
}
