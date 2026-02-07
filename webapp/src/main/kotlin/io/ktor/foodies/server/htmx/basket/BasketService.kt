package io.ktor.foodies.server.htmx.basket

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Client interface for the Basket microservice. */
interface BasketService {
    /**
     * Get the current user's basket.
     *
     * @return The user's basket (may be empty)
     */
    suspend fun getBasket(): CustomerBasket

    suspend fun count(): Int =
        runCatching { getBasket().items.sumOf { it.quantity } }.getOrDefault(0)

    /**
     * Add an item to the basket. If the menu item already exists, increments the quantity.
     *
     * @param menuItemId The ID of the menu item to add
     * @param quantity The quantity to add (must be >= 1)
     * @return The updated basket
     */
    suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket

    /**
     * Update the quantity of an item in the basket.
     *
     * @param itemId The basket item ID (not menu item ID)
     * @param quantity The new quantity (must be >= 1)
     * @return The updated basket
     */
    suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket

    /**
     * Remove an item from the basket.
     *
     * @param itemId The basket item ID to remove
     * @return The updated basket
     */
    suspend fun removeItem(itemId: String): CustomerBasket

    /** Clear the entire basket. */
    suspend fun clearBasket()
}

/** HTTP implementation of the BasketService that calls the Basket microservice. */
class HttpBasketService(baseUrl: String, private val httpClient: HttpClient) : BasketService {
    private val basketBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getBasket(): CustomerBasket =
        httpClient.get("$basketBaseUrl/basket").body()

    override suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket =
        httpClient
            .post("$basketBaseUrl/basket/items") {
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId, quantity))
            }
            .body()

    override suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket =
        httpClient
            .put("$basketBaseUrl/basket/items/$itemId") {
                contentType(ContentType.Application.Json)
                setBody(UpdateQuantityRequest(quantity))
            }
            .body()

    override suspend fun removeItem(itemId: String): CustomerBasket =
        httpClient.delete("$basketBaseUrl/basket/items/$itemId").body()

    override suspend fun clearBasket() {
        httpClient.delete("$basketBaseUrl/basket")
    }
}
