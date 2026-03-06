package io.ktor.foodies.webapp.menu

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.foodies.webapp.menu.toDomain
import kotlinx.serialization.Serializable

class HttpMenuService(baseUrl: String, private val httpClient: HttpClient) : io.ktor.foodies.webapp.menu.MenuService {
    private val menuBaseUrl = baseUrl.trimEnd('/')

    override suspend fun menuItems(offset: Int, limit: Int): List<io.ktor.foodies.webapp.menu.MenuItem> =
        httpClient.get("$menuBaseUrl/menu") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.body<List<io.ktor.foodies.webapp.menu.MenuItemResponse>>().map { it.toDomain() }
}

@Serializable
private data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val stock: Int
)

private fun io.ktor.foodies.webapp.menu.MenuItemResponse.toDomain(): io.ktor.foodies.webapp.menu.MenuItem =
    _root_ide_package_.io.ktor.foodies.webapp.menu.MenuItem(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        price = price
    )
