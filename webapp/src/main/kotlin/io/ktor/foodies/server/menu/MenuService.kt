package io.ktor.foodies.server.menu

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable

@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal
)

interface MenuService {
    suspend fun menuItems(offset: Int, limit: Int): List<MenuItem>
    suspend fun getMenuItem(id: Long): MenuItem?
}

class HttpMenuService(baseUrl: String, private val httpClient: HttpClient) : MenuService {
    private val menuBaseUrl = baseUrl.trimEnd('/')

    override suspend fun menuItems(offset: Int, limit: Int): List<MenuItem> =
        httpClient.get("$menuBaseUrl/menu") {
            parameter("offset", offset)
            parameter("limit", limit)
        }.body<List<MenuItemResponse>>().map { it.toDomain() }

    override suspend fun getMenuItem(id: Long): MenuItem? =
        httpClient.get("$menuBaseUrl/menu/$id").run {
            if (status.value == 404) null
            else body<MenuItemResponse>().toDomain()
        }
}

@Serializable
private data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal
)

private fun MenuItemResponse.toDomain(): MenuItem = MenuItem(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    price = price
)
