package io.ktor.foodies.menu.stock

import io.ktor.foodies.events.order.StockValidationItem

interface StockRepository {
    fun validateAndReserveStock(items: List<StockValidationItem>): StockValidationResult
    fun returnStock(items: List<StockValidationItem>)
}
