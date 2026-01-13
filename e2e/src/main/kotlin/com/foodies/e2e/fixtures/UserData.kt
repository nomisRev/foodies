package com.foodies.e2e.fixtures

data class TestUser(
    val username: String,
    val password: String,
    val email: String
)

object UserData {
    val regular = TestUser(
        username = System.getenv("TEST_USERNAME") ?: "food_lover",
        password = System.getenv("TEST_PASSWORD") ?: "password",
        email = "food_lover@gmail.com"
    )

    val admin = TestUser(
        username = System.getenv("ADMIN_USERNAME") ?: "admin",
        password = System.getenv("ADMIN_PASSWORD") ?: "admin_password",
        email = "admin@foodies.com"
    )
}
