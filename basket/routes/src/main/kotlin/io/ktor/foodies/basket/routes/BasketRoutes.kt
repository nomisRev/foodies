package io.ktor.foodies.basket.routes

import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.DynamicResource
import opensavvy.spine.api.RootResource
import opensavvy.spine.api.StaticResource

object BasketApi : RootResource("basket") {
    val get by get().response<CustomerBasket>()
    val clear by delete()

    object Items : StaticResource<BasketApi>("items", parent = BasketApi) {
        val add by post()
            .request<AddItemRequest>()
            .response<CustomerBasket>()
            .failure<BasketError.InvalidRequest>(HttpStatusCode.BadRequest)
            .failure<BasketError.NotFound>(HttpStatusCode.NotFound)

        object Item : DynamicResource<Items>("itemId", parent = Items) {
            val update by put()
                .request<UpdateItemQuantityRequest>()
                .response<CustomerBasket>()
                .failure<BasketError.InvalidRequest>(HttpStatusCode.BadRequest)
                .failure<BasketError.NotFound>(HttpStatusCode.NotFound)

            val remove by delete()
                .response<CustomerBasket>()
                .failure<BasketError.NotFound>(HttpStatusCode.NotFound)
        }
    }
}
