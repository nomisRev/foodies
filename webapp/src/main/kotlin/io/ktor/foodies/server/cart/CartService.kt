package io.ktor.foodies.server.cart

interface CartService {
    suspend fun getBasket(): CustomerBasket
    suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket
    suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket
    suspend fun removeItem(itemId: String): CustomerBasket
    suspend fun clearBasket()
}
