package io.ktor.foodies.payment

import io.ktor.foodies.server.openid.authenticated
import io.ktor.foodies.server.openid.authenticatedUser
import io.ktor.foodies.server.openid.requireAdminOrScope
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.paymentRoutes(paymentService: PaymentService) {
    // Admin endpoint - require admin role or payment:read scope
    authenticated {
        requireAdminOrScope("payment:read") {
            get("/payments/{orderId}") {
                val orderId = call.parameters["orderId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid order ID")

                val payment = paymentService.getPaymentByOrderId(orderId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Payment not found")

                call.respond(payment)
            }
        }
    }
}
