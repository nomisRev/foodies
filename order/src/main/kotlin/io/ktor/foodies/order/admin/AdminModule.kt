package io.ktor.foodies.order.admin

import io.ktor.foodies.order.fulfillment.FulfillmentModule
import io.ktor.foodies.order.fulfillment.FulfillmentService
import io.ktor.foodies.order.tracking.TrackingModule
import io.ktor.foodies.order.tracking.TrackingService

data class AdminModule(
    val trackingService: TrackingService,
    val fulfillmentService: FulfillmentService,
)

fun adminModule(tracking: TrackingModule, fulfillment: FulfillmentModule): AdminModule =
    AdminModule(tracking.service, fulfillment.service)
