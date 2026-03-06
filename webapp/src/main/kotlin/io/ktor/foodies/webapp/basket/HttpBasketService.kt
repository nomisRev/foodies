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
    io.ktor.foodies.webapp.basket.BasketService {
    private val basketBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getBasket(): io.ktor.foodies.webapp.basket.CustomerBasket =
        httpClient.get("$basketBaseUrl/basket").body()

    override suspend fun addItem(menuItemId: Long, quantity: Int): io.ktor.foodies.webapp.basket.CustomerBasket =
        httpClient.post("$basketBaseUrl/basket/items") {
            contentType(ContentType.Application.Json)
            setBody(_root_ide_package_.io.ktor.foodies.webapp.basket.AddItemRequest(menuItemId, quantity))
        }.body()

    override suspend fun updateItemQuantity(itemId: String, quantity: Int): io.ktor.foodies.webapp.basket.CustomerBasket =
        httpClient.put("$basketBaseUrl/basket/items/$itemId") {
            contentType(ContentType.Application.Json)
            setBody(_root_ide_package_.io.ktor.foodies.webapp.basket.UpdateQuantityRequest(quantity))
        }.body()

    override suspend fun removeItem(itemId: String): io.ktor.foodies.webapp.basket.CustomerBasket =
        httpClient.delete("$basketBaseUrl/basket/items/$itemId").body()

    override suspend fun clearBasket() {
        httpClient.delete("$basketBaseUrl/basket")
    }
}
