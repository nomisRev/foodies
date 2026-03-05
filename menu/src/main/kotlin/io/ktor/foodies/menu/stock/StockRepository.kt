package io.ktor.foodies.menu.stock

import io.ktor.foodies.events.order.StockValidationItem
import io.ktor.foodies.menu.persistence.MenuRepository

interface StockRepository : MenuRepository {
    fun validateAndReserveStock(items: List<StockValidationItem>): StockValidationResult
    fun returnStock(items: List<StockValidationItem>)
}
