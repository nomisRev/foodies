package io.ktor.foodies.server.htmx.basket

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
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
     * @param token The JWT token for authentication
     * @return The user's basket (may be empty)
     */
    suspend fun getBasket(token: String): CustomerBasket

    /**
     * Add an item to the basket. If the menu item already exists, increments the quantity.
     *
     * @param token The JWT token for authentication
     * @param menuItemId The ID of the menu item to add
     * @param quantity The quantity to add (must be >= 1)
     * @return The updated basket
     */
    suspend fun addItem(token: String, menuItemId: Long, quantity: Int): CustomerBasket

    /**
     * Update the quantity of an item in the basket.
     *
     * @param token The JWT token for authentication
     * @param itemId The basket item ID (not menu item ID)
     * @param quantity The new quantity (must be >= 1)
     * @return The updated basket
     */
    suspend fun updateItemQuantity(token: String, itemId: String, quantity: Int): CustomerBasket

    /**
     * Remove an item from the basket.
     *
     * @param token The JWT token for authentication
     * @param itemId The basket item ID to remove
     * @return The updated basket
     */
    suspend fun removeItem(token: String, itemId: String): CustomerBasket

    /**
     * Clear the entire basket.
     *
     * @param token The JWT token for authentication
     */
    suspend fun clearBasket(token: String)
}

/** HTTP implementation of the BasketService that calls the Basket microservice. */
class HttpBasketService(baseUrl: String, private val httpClient: HttpClient) : BasketService {
    private val basketBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getBasket(token: String): CustomerBasket =
        httpClient.get("$basketBaseUrl/basket") { bearerAuth(token) }.body()

    override suspend fun addItem(token: String, menuItemId: Long, quantity: Int): CustomerBasket =
        httpClient
            .post("$basketBaseUrl/basket/items") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(AddItemRequest(menuItemId, quantity))
            }
            .body()

    override suspend fun updateItemQuantity(
        token: String,
        itemId: String,
        quantity: Int,
    ): CustomerBasket =
        httpClient
            .put("$basketBaseUrl/basket/items/$itemId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(UpdateQuantityRequest(quantity))
            }
            .body()

    override suspend fun removeItem(token: String, itemId: String): CustomerBasket =
        httpClient.delete("$basketBaseUrl/basket/items/$itemId") { bearerAuth(token) }.body()

    override suspend fun clearBasket(token: String) {
        httpClient.delete("$basketBaseUrl/basket") { bearerAuth(token) }
    }
}
