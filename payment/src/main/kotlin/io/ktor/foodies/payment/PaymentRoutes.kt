package io.ktor.foodies.payment

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.paymentRoutes(paymentService: PaymentService) {
    get("/payments/{orderId}") {
        val orderId = call.parameters["orderId"]?.toLongOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid order ID")

        val payment = paymentService.getPaymentByOrderId(orderId)
            ?: return@get call.respond(HttpStatusCode.NotFound, "Payment not found")

        call.respond(payment)
    }
}
