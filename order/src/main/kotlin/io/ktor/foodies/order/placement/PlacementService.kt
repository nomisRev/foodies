package io.ktor.foodies.order.placement

import io.ktor.foodies.events.order.OrderCreatedEvent
import io.ktor.foodies.events.order.OrderItemSnapshot
import io.ktor.foodies.basket.routes.BasketClient
import io.ktor.foodies.order.Order
import io.ktor.foodies.order.OrderConfig
import io.ktor.foodies.server.validate
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

interface PlacementService {
    suspend fun createOrder(
        requestId: UUID,
        buyerId: String,
        buyerEmail: String,
        buyerName: String,
        request: CreateOrderRequest,
        token: String
    ): Order
}

class DefaultPlacementService(
    private val placementRepository: PlacementRepository,
    private val basketClient: BasketClient,
    private val eventPublisher: PlacementEventPublisher,
    private val config: OrderConfig,
) : PlacementService {

    override suspend fun createOrder(
        requestId: UUID,
        buyerId: String,
        buyerEmail: String,
        buyerName: String,
        request: CreateOrderRequest,
        token: String
    ): Order {
        placementRepository.findByRequestId(requestId.toString())?.let { return it }

        val address = request.validate()
        val basket = basketClient.getBasket() ?: throw IllegalArgumentException("Basket not found")
        if (basket.items.isEmpty()) throw IllegalArgumentException("Basket is empty")

        val createOrder = CreateOrder(
            requestId = requestId.toString(),
            buyerId = buyerId,
            buyerEmail = buyerEmail,
            buyerName = buyerName,
            deliveryAddress = address,
            items = basket.items.map {
                CreateOrderItem(
                    menuItemId = it.menuItemId,
                    menuItemName = it.menuItemName,
                    pictureUrl = it.menuItemImageUrl,
                    unitPrice = it.unitPrice,
                    quantity = it.quantity,
                )
            },
            totalPrice = basket.items.sumOf { it.unitPrice * it.quantity.toBigDecimal() },
            currency = request.currency,
            paymentDetails = request.paymentDetails
        )

        val order = placementRepository.create(createOrder)

        val event = OrderCreatedEvent(
            orderId = order.id,
            buyerId = order.buyerId,
            items = order.items.map { OrderItemSnapshot(it.menuItemId, it.quantity, it.unitPrice) },
            totalPrice = order.totalPrice,
            currency = order.currency,
            createdAt = order.createdAt
        )
        eventPublisher.publish(event)
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val gracePeriodEvent = GracePeriodExpiredEvent(
            orderId = order.id,
            expiredAt = now + config.gracePeriodSeconds.seconds
        )
        eventPublisher.publish(gracePeriodEvent, config.gracePeriodSeconds.seconds)

        return order
    }

    private fun CreateOrderRequest.validate() = validate {
        paymentDetails.cardNumber.validate({ it.length in 13..19 }) { "Card number must be 13-19 digits" }
        paymentDetails.cardSecurityNumber.validate({ it.length in 3..4 }) { "CVV must be 3-4 digits" }
        paymentDetails.cardHolderName.validate({ it.isNotBlank() }) { "Card holder name is required" }
        paymentDetails.validate({ it.isNotExpired() }) { "Card is expired" }

        io.ktor.foodies.order.Address(
            street = street.validate({ it.isNotBlank() }) { "street is required" },
            city = city.validate({ it.isNotBlank() }) { "city is required" },
            state = state.validate({ it.isNotBlank() }) { "state is required" },
            country = country.validate({ it.isNotBlank() }) { "country is required" },
            zipCode = zipCode.validate({ it.isNotBlank() }) { "zipCode is required" }
        )
    }
}
