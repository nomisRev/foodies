package io.ktor.foodies.order.service

import io.ktor.foodies.order.OrderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class GracePeriodService(
    private val config: OrderConfig,
    private val orderService: OrderService,
    private val scope: CoroutineScope
) {
    fun scheduleGracePeriodExpiration(orderId: Long) {
        scope.launch {
            delay(config.gracePeriodSeconds.seconds)
            orderService.transitionToAwaitingValidation(orderId)
        }
    }
}
