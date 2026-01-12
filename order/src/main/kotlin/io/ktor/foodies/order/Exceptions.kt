package io.ktor.foodies.order

class OrderNotFoundException(orderId: Long) : RuntimeException("Order $orderId not found")
class OrderForbiddenException(orderId: Long) : RuntimeException("Order $orderId belongs to a different user")
