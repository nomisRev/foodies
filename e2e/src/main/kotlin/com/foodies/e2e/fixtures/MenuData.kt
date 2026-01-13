package com.foodies.e2e.fixtures

import java.math.BigDecimal

data class TestMenuItem(
    val name: String,
    val description: String,
    val price: BigDecimal,
    val imageUrl: String
)

object MenuData {
    val testMenuItems = listOf(
        TestMenuItem(
            name = "Margherita Pizza",
            description = "Classic tomato and mozzarella pizza",
            price = BigDecimal("12.99"),
            imageUrl = "https://example.com/pizza.jpg"
        ),
        TestMenuItem(
            name = "Caesar Salad",
            description = "Fresh romaine with parmesan and croutons",
            price = BigDecimal("8.99"),
            imageUrl = "https://example.com/salad.jpg"
        )
        // Add more test data as needed
    )
}
