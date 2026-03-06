package io.ktor.foodies.menu.stock

import io.ktor.foodies.events.menu.RejectedItem
import io.ktor.foodies.events.order.StockValidationItem
import kotlin.time.Instant

sealed interface StockValidationResult {
    data class Success(val confirmedAt: Instant) : StockValidationResult
    data class Failure(val rejectedItems: List<RejectedItem>, val rejectedAt: Instant) : StockValidationResult
}

interface StockService {
    fun validateAndReserveStock(orderId: Long, items: List<StockValidationItem>): StockValidationResult
    fun returnStock(orderId: Long, items: List<StockValidationItem>)
}

class StockServiceImpl(private val repository: StockRepository) : StockService {
    override fun validateAndReserveStock(orderId: Long, items: List<StockValidationItem>): StockValidationResult =
        repository.validateAndReserveStock(items)

    override fun returnStock(orderId: Long, items: List<StockValidationItem>) =
        repository.returnStock(items)
}
