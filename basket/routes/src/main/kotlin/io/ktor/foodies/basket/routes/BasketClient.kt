package io.ktor.foodies.basket.routes

import io.ktor.client.HttpClient
import opensavvy.spine.api.div
import opensavvy.spine.api.invoke
import opensavvy.spine.api.resolved
import opensavvy.spine.client.bodyOrThrow
import opensavvy.spine.client.request

fun BasketClient(httpClient: HttpClient): BasketClient = KtorBasketClient(httpClient)

interface BasketClient {
    suspend fun getBasket(): CustomerBasket?
    suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket
    suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket
    suspend fun removeItem(itemId: String): CustomerBasket
    suspend fun clearBasket()
}

private class KtorBasketClient(private val httpClient: HttpClient) : BasketClient {
    override suspend fun getBasket(): CustomerBasket =
        httpClient.request(BasketApi.resolved / BasketApi.get).bodyOrThrow()

    override suspend fun addItem(menuItemId: Long, quantity: Int): CustomerBasket =
        httpClient.request(
            BasketApi / BasketApi.Items / BasketApi.Items.add,
            AddItemRequest(menuItemId, quantity)
        ).bodyOrThrow()

    override suspend fun updateItemQuantity(itemId: String, quantity: Int): CustomerBasket =
        httpClient.request(
            BasketApi / BasketApi.Items / BasketApi.Items.Item(itemId) / BasketApi.Items.Item.update,
            UpdateItemQuantityRequest(quantity)
        ).bodyOrThrow()

    override suspend fun removeItem(itemId: String): CustomerBasket =
        httpClient.request(BasketApi / BasketApi.Items / BasketApi.Items.Item(itemId) / BasketApi.Items.Item.remove)
            .bodyOrThrow()

    override suspend fun clearBasket() {
        httpClient.request(BasketApi.resolved / BasketApi.clear).bodyOrThrow()
    }
}