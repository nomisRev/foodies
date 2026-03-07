package io.ktor.foodies.webapp.menu

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
}
