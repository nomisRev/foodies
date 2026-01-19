package io.ktor.foodies.payment

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.paymentRoutes(paymentService: PaymentService) {
    // Admin endpoint - would require authentication in production
    get("/payments/{orderId}") {
        val orderId =
            call.parameters["orderId"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid order ID")

        val payment =
            paymentService.getPaymentByOrderId(orderId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Payment not found")

        call.respond(payment)
    }
}
