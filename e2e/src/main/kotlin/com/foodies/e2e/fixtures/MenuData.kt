package com.foodies.e2e.fixtures

data class MenuItem(
    val name: String,
    val description: String,
    val price: String
)

object MenuData {
    val items = listOf(
        MenuItem("Margherita", "Classic pizza with tomato sauce and mozzarella", "9.50"),
        MenuItem("Pasta Carbonara", "Creamy pasta with pancetta and egg", "12.00"),
        MenuItem("Beef Burger", "Juicy beef patty with lettuce and cheese", "10.00")
    )
}
