package com.foodies.e2e.fixtures

data class User(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String
)

object UserData {
    val defaultUser = User(
        email = "food_lover@gmail.com",
        password = "password",
        firstName = "Food",
        lastName = "Lover"
    )
}
