package io.ktor.foodies.basket.routes

import io.ktor.http.HttpStatusCode
import opensavvy.spine.api.DynamicResource
import opensavvy.spine.api.RootResource
import opensavvy.spine.api.StaticResource

object BasketApi : RootResource("basket") {

    val Get by get()
        .response<CustomerBasket>()

    val Clear by delete()

    object Items : StaticResource<BasketApi>("items", parent = BasketApi) {

        val AddItem by post()
            .request<AddItemRequest>()
            .response<CustomerBasket>()
            .failure<String>(HttpStatusCode.BadRequest)
            .failure<String>(HttpStatusCode.NotFound)

        object Item : DynamicResource<Items>("itemId", parent = Items) {

            val UpdateItem by put()
                .request<UpdateItemQuantityRequest>()
                .response<CustomerBasket>()
                .failure<String>(HttpStatusCode.BadRequest)
                .failure<String>(HttpStatusCode.NotFound)

            val RemoveItem by delete()
                .response<CustomerBasket>()
                .failure<String>(HttpStatusCode.NotFound)
        }
    }
}
