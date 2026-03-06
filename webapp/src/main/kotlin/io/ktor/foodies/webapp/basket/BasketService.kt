package io.ktor.foodies.webapp.basket

interface BasketService {
    suspend fun getBasket(): io.ktor.foodies.webapp.basket.CustomerBasket
    suspend fun addItem(menuItemId: Long, quantity: Int): io.ktor.foodies.webapp.basket.CustomerBasket
    suspend fun updateItemQuantity(itemId: String, quantity: Int): io.ktor.foodies.webapp.basket.CustomerBasket
    suspend fun removeItem(itemId: String): io.ktor.foodies.webapp.basket.CustomerBasket
    suspend fun clearBasket()
}
