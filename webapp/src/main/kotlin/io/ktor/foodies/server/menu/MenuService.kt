package io.ktor.foodies.server.menu

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.foodies.server.SerializableBigDecimal
import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val description: String,
    val iconUrl: String?
)

@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val categoryId: Long,
    val categoryName: String
)

interface MenuService {
    suspend fun menuItems(offset: Int, limit: Int, categoryId: Long? = null): List<MenuItem>
    suspend fun getMenuItem(id: Long): MenuItem?
    suspend fun searchMenuItems(query: String): List<MenuItem>
    suspend fun getCategories(): List<Category>
}

class HttpMenuService(baseUrl: String, private val httpClient: HttpClient) : MenuService {
    private val menuBaseUrl = baseUrl.trimEnd('/')

    override suspend fun menuItems(offset: Int, limit: Int, categoryId: Long?): List<MenuItem> =
        httpClient.get("$menuBaseUrl/menu") {
            parameter("offset", offset)
            parameter("limit", limit)
            if (categoryId != null) parameter("categoryId", categoryId)
        }.body<List<MenuItemResponse>>().map { it.toDomain() }

    override suspend fun getMenuItem(id: Long): MenuItem? =
        httpClient.get("$menuBaseUrl/menu/$id").run {
            if (status.value == 404) null
            else body<MenuItemResponse>().toDomain()
        }

    override suspend fun searchMenuItems(query: String): List<MenuItem> =
        httpClient.get("$menuBaseUrl/menu/search") {
            parameter("q", query)
        }.body<List<MenuItemResponse>>().map { it.toDomain() }

    override suspend fun getCategories(): List<Category> =
        httpClient.get("$menuBaseUrl/menu/categories").body<List<CategoryResponse>>().map { it.toDomain() }
}

@Serializable
private data class CategoryResponse(
    val id: Long,
    val name: String,
    val description: String,
    val iconUrl: String?
)

private fun CategoryResponse.toDomain(): Category = Category(
    id = id,
    name = name,
    description = description,
    iconUrl = iconUrl
)

@Serializable
private data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val categoryId: Long,
    val categoryName: String
)

private fun MenuItemResponse.toDomain(): MenuItem = MenuItem(
    id = id,
    name = name,
    description = description,
    imageUrl = imageUrl,
    price = price,
    categoryId = categoryId,
    categoryName = categoryName
)
