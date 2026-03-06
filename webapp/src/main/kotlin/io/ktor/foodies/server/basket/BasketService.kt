package io.ktor.foodies.server.basket

interface BasketService {
    suspend fun getBasket(): CustomerBasket
    suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket
    suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket
    suspend fun removeItem(itemId: String): CustomerBasket
    suspend fun clearBasket()
}
