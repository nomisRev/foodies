package io.ktor.foodies.webapp.basket

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class HttpBasketService(baseUrl: String, private val httpClient: HttpClient) :
    BasketService {
    private val basketBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getBasket(): CustomerBasket =
        httpClient.get("$basketBaseUrl/basket").body()

    override suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket =
        httpClient.post("$basketBaseUrl/basket/items") {
            contentType(ContentType.Application.Json)
            setBody(AddItemRequest(menuItemId, quantity))
        }.body()

    override suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket =
        httpClient.put("$basketBaseUrl/basket/items/$itemId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateQuantityRequest(quantity))
        }.body()

    override suspend fun removeItem(itemId: String): CustomerBasket =
        httpClient.delete("$basketBaseUrl/basket/items/$itemId").body()

    override suspend fun clearBasket() {
        httpClient.delete("$basketBaseUrl/basket")
    }
}
