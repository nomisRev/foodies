package io.ktor.foodies.server.menu

import kotlinx.serialization.Serializable

@Serializable
data class MenuItem(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val price: Double
)

interface MenuService {
    suspend fun menuItems(offset: Int, limit: Int): List<MenuItem>
}

class InMemoryMenuService : MenuService {
    private val totalItems = 200

    override suspend fun menuItems(offset: Int, limit: Int): List<MenuItem> {
        val safeOffset = offset.coerceAtLeast(0)
        if (safeOffset >= totalItems) return emptyList()

        val safeLimit = limit.coerceIn(1, 50)
        val endExclusive = (safeOffset + safeLimit).coerceAtMost(totalItems)

        return (safeOffset until endExclusive).map { generate(it.toLong()) }
    }

    private fun generate(id: Long) = MenuItem(
        id = id,
        name = "Seasonal special #$id",
        description = "Freshly crafted dish number $id with locally sourced ingredients.",
        imageUrl = "https://img.freepik.com/free-photo/top-view-table-full-food_23-2149209253.jpg?semt=ais_hybrid&w=740&q=80",
        price = 8.99 + (id % 15) * 0.75
    )
}
