package io.ktor.foodies.basket

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