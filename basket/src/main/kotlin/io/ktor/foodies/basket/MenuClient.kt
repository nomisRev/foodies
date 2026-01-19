package io.ktor.foodies.basket

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Menu item data from the Menu service.
 */
@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: SerializableBigDecimal,
    val stock: Int
)

/**
 * Client interface for the Menu service.
 */
interface MenuClient {
    suspend fun getMenuItem(id: Long): MenuItem?
}

/**
 * HTTP-based implementation of MenuClient.
 */
class HttpMenuClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : MenuClient {

    private val menuBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getMenuItem(id: Long): MenuItem? {
        return try {
            httpClient.get("$menuBaseUrl/menu/$id").body<MenuItem>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null
            else throw e
        }
    }
}

/**
 * In-memory implementation for testing purposes.
 */
class InMemoryMenuClient(
    private val menuItems: MutableMap<Long, MenuItem> = mutableMapOf()
) : MenuClient {

    fun addMenuItem(item: MenuItem) {
        menuItems[item.id] = item
    }

    override suspend fun getMenuItem(id: Long): MenuItem? = menuItems[id]
}
