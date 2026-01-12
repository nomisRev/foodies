package io.ktor.foodies.basket

/**
 * In-memory implementation for testing purposes.
 */
class InMemoryBasketRepository : BasketRepository {
    private val baskets = mutableMapOf<String, CustomerBasket>()

    override suspend fun getBasket(buyerId: String): CustomerBasket? = baskets[buyerId]

    override suspend fun updateBasket(basket: CustomerBasket): CustomerBasket {
        baskets[basket.buyerId] = basket
        return basket
    }

    override suspend fun deleteBasket(buyerId: String): Boolean = baskets.remove(buyerId) != null
}