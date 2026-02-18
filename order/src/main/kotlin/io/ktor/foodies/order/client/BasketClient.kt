package io.ktor.foodies.order.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.foodies.server.SerializableBigDecimal
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class BasketItem(
    val menuItemId: Long,
    val menuItemName: String,
    val menuItemImageUrl: String,
    val unitPrice: SerializableBigDecimal,
    val quantity: Int
)

@Serializable
data class CustomerBasket(
    val buyerId: String,
    val items: List<BasketItem>
)

interface BasketClient {
    suspend fun getBasket(buyerId: String, token: String): CustomerBasket?
}

class HttpBasketClient(
    private val httpClient: HttpClient,
    baseUrl: String
) : BasketClient {
    private val basketBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getBasket(buyerId: String, token: String): CustomerBasket? =
        try {
            httpClient.get("$basketBaseUrl/basket").body<CustomerBasket>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
}
